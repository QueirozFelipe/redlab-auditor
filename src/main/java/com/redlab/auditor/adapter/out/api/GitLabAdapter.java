package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class GitLabAdapter implements SourceControlPort {

    private Semaphore rateLimiter;

    @Override
    public List<Commit> fetchCommitsSinceLastTag(Profile profile, String targetBranch) {
        GitLabApi gitLabApi = new GitLabApi(profile.gitlabUrl(), profile.gitlabToken());
        this.rateLimiter = new Semaphore(profile.gitlabRateLimit());

        try {
            List<Project> projects = gitLabApi.getGroupApi().getProjects(profile.gitlabGroupId());
            List<Commit> allCommits = new ArrayList<>();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

                List<Callable<List<Commit>>> tasks = projects.stream()
                        .map(project -> (Callable<List<Commit>>) () -> processProject(gitLabApi, project, profile, targetBranch))
                        .toList();

                List<Future<List<Commit>>> results = executor.invokeAll(tasks);

                for (Future<List<Commit>> result : results) {
                    allCommits.addAll(result.get());
                }
            }

            return allCommits;

        } catch (GitLabApiException | InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Futuramente lançar uma exceção de domínio aqui (ex: AuditException)
            throw new RuntimeException("Error fetching data from GitLab: " + e.getMessage(), e);
        }
    }

    private List<Commit> processProject(GitLabApi gitLabApi, Project project, Profile profile, String targetBranch) throws InterruptedException {
        rateLimiter.acquire();
        try {
            return fetchWithFallback(gitLabApi, project, profile, targetBranch);
        } finally {
            rateLimiter.release();
        }
    }

    private List<Commit> fetchWithFallback(GitLabApi gitLabApi, Project project, Profile profile, String target) {
        try {
            return executeGitLabCompare(gitLabApi, project, profile, target);
        } catch (GitLabApiException e) {
            if (e.getHttpStatus() == 404 && profile.secondaryTargetBranch() != null && !profile.secondaryTargetBranch().isBlank()) {
                try {
                    System.out.println("    [!] Branch " + target + " not found in " + project.getName() + ". Trying fallback: " + profile.secondaryTargetBranch());
                    return executeGitLabCompare(gitLabApi, project, profile, profile.secondaryTargetBranch());
                } catch (GitLabApiException e2) {
                    return List.of();
                }
            }
            return List.of();
        }
    }

    private List<Commit> executeGitLabCompare(GitLabApi gitLabApi, Project project, Profile profile, String target) throws GitLabApiException {
        List<Tag> tags = gitLabApi.getTagsApi().getTags(project.getId());
        if (tags.isEmpty()) return List.of();

        String latestTagName = tags.get(0).getName();

        CompareResults comparison = gitLabApi.getRepositoryApi()
          .compare(project.getId(), latestTagName, target);

        return comparison.getCommits().stream()
          .map(gitlabCommit -> mapToDomainCommit(gitlabCommit, profile.taskRegex()))
          .collect(Collectors.toList());
    }

    private Commit mapToDomainCommit(org.gitlab4j.api.models.Commit gitlabCommit, String regex) {
        List<String> associatedTasks = extractTaskIdsFromMessage(gitlabCommit.getMessage(), regex);
        return new Commit(
          gitlabCommit.getId(),
          gitlabCommit.getMessage(),
          gitlabCommit.getAuthorName(),
          associatedTasks,
          gitlabCommit.getWebUrl()
        );
    }

    private List<String> extractTaskIdsFromMessage(String message, String regex) {
        List<String> taskIds = new ArrayList<>();
        if (message != null && regex != null) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(message);
            while (matcher.find()) {
                taskIds.add(matcher.group());
            }
        }
        return taskIds;
    }

}
