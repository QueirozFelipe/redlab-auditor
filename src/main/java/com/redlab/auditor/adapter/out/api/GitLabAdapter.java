package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.SourceControlInfo;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.CompareResults;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class GitLabAdapter implements SourceControlPort {

    private Semaphore rateLimiter;

    private record ProjectResult(List<Commit> commits, String projectName, boolean isValid) {
    }

    private static final Pattern IGNORE_COMMIT_PATTERN = Pattern.compile(
            "^(Merge branch|Merge pull request|See merge request|Merge tag|chore:?\\s*release).*",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        this.rateLimiter = new Semaphore(profile.gitlabRateLimit());

        try (GitLabApi gitLabApi = new GitLabApi(profile.gitlabUrl(), profile.gitlabToken())) {
            Group group = gitLabApi.getGroupApi().getGroup(profile.gitlabGroupId());
            String groupName = group.getName();
            String groupId = String.valueOf(group.getId());

            List<Project> projects = gitLabApi.getGroupApi().getProjects(profile.gitlabGroupId());

            List<Commit> allCommits = new ArrayList<>();
            List<String> activeProjects = new ArrayList<>();
            List<String> ignoredProjects = new ArrayList<>();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

                List<Callable<ProjectResult>> tasks = projects.stream()
                        .map(project -> (Callable<ProjectResult>) () -> processProject(gitLabApi, project, profile, sourceBranches, targetBranches))
                        .toList();

                List<Future<ProjectResult>> results = executor.invokeAll(tasks);

                for (Future<ProjectResult> resultFuture : results) {
                    ProjectResult pr = resultFuture.get();

                    allCommits.addAll(pr.commits());

                    if (!pr.isValid()) {
                        ignoredProjects.add(pr.projectName());
                    } else if (!pr.commits().isEmpty()) {
                        activeProjects.add(pr.projectName());
                    }
                }
            }

            int totalProjects = projects.size();
            int validProjectsCount = totalProjects - ignoredProjects.size();

            SourceControlInfo scInfo = new SourceControlInfo(
                    "GitLab",
                    profile.gitlabUrl(),
                    groupName,
                    groupId,
                    totalProjects,
                    validProjectsCount,
                    activeProjects.size()
            );

            return new SourceControlResult(scInfo, allCommits, activeProjects, ignoredProjects);

        } catch (GitLabApiException | InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error fetching data from GitLab: " + e.getMessage(), e);
        }
    }

    private ProjectResult processProject(GitLabApi gitLabApi,
                                         Project project,
                                         Profile profile,
                                         List<String> sourceBranches,
                                         List<String> targetBranches) throws InterruptedException {
        rateLimiter.acquire();
        try {
            System.out.println("  [>] Auditing project: " + project.getName());
            return executeWithFailover(gitLabApi, project, profile, sourceBranches, targetBranches);
        } finally {
            rateLimiter.release();
        }
    }

    private ProjectResult executeWithFailover(GitLabApi gitLabApi, Project project, Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        for (String target : targetBranches) {
            for (String source : sourceBranches) {
                try {
                    CompareResults comparison = gitLabApi.getRepositoryApi()
                            .compare(project.getId(), target, source);

                    List<Commit> commits = comparison.getCommits().stream()
                            .filter(this::isMeaningfulCommit)
                            .map(gitlabCommit -> mapToDomainCommit(gitlabCommit, profile.taskRegex()))
                            .collect(Collectors.toList());

                    return new ProjectResult(commits, project.getName(), true);

                } catch (GitLabApiException e) {
                    if (e.getHttpStatus() == 404) {
                        continue;
                    }
                    System.err.println("    [!] Error comparing " + target + "..." + source + " in " + project.getName() + ": " + e.getMessage());
                    break;
                }
            }
        }
        // If valid branches were not found
        return new ProjectResult(List.of(), project.getName(), false);
    }

    private boolean isMeaningfulCommit(org.gitlab4j.api.models.Commit commit) {
        if (commit.getMessage() == null) return false;
        return !IGNORE_COMMIT_PATTERN.matcher(commit.getMessage().trim()).find();
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