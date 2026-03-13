package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.infrastructure.config.RedLabAuditorConfig;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    private final RedLabAuditorConfig config;
    private final Semaphore rateLimiter;
    private GitLabApi gitLabApi;
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("\\b\\d{6}\\b");

    @Inject
    public GitLabAdapter(RedLabAuditorConfig config) {
        this.config = config;
        this.rateLimiter = new Semaphore(10); // Futuramente externalizar numa variável de ambiente
    }

    @PostConstruct
    public void initialize() {
        this.gitLabApi = new GitLabApi(config.gitlab().url(), config.gitlab().personalAccessToken());
    }

    @Override
    public List<Commit> fetchCommitsSinceLastTag(String productionBranch, String targetBranch) {
        try {
            List<Project> projects = gitLabApi.getGroupApi().getProjects(config.gitlab().groupId());
            List<Commit> allCommits = new ArrayList<>();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

                List<Callable<List<Commit>>> tasks = projects.stream()
                        .map(project -> (Callable<List<Commit>>) () -> processProject(project, productionBranch, targetBranch))
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

    private List<Commit> processProject(Project project, String productionBranch, String targetBranch) throws GitLabApiException, InterruptedException {
        rateLimiter.acquire();

        try {
            List<Tag> tags = gitLabApi.getTagsApi().getTags(project.getId());

            if (tags.isEmpty()) {
                return List.of();
            }

            String latestTagName = tags.get(0).getName();

            CompareResults comparison = gitLabApi.getRepositoryApi()
                    .compare(project.getId(), latestTagName, targetBranch);

            return comparison.getCommits().stream()
                    .map(gitlabCommit -> mapToDomainCommit(gitlabCommit, project))
                    .collect(Collectors.toList());
        } finally {
            rateLimiter.release();
        }
    }

    private Commit mapToDomainCommit(org.gitlab4j.api.models.Commit gitlabCommit, Project project) {
        List<String> associatedTasks = extractTaskIdsFromMessage(gitlabCommit.getMessage());

        return new Commit(
                gitlabCommit.getId(),
                gitlabCommit.getMessage(),
                gitlabCommit.getAuthorName(),
                associatedTasks,
                gitlabCommit.getWebUrl()
        );
    }

    private List<String> extractTaskIdsFromMessage(String message) {
        List<String> taskIds = new ArrayList<>();
        if (message != null) {
            Matcher matcher = TASK_ID_PATTERN.matcher(message);
            while (matcher.find()) {
                taskIds.add(matcher.group());
            }
        }
        return taskIds;
    }

}
