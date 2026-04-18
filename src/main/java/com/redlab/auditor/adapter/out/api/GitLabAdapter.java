package com.redlab.auditor.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.redlab.auditor.adapter.out.api.client.GitLabClient;
import com.redlab.auditor.domain.exception.ResourceNotFoundException;
import com.redlab.auditor.domain.model.*;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Named("GITLAB")
public class GitLabAdapter implements SourceControlPort {

    private Semaphore rateLimiter;
    private static final Pattern IGNORE_COMMIT_PATTERN = Pattern.compile(
            "^(Merge branch|Merge pull request|See merge request|Merge tag|chore:?\\s*release).*",
            Pattern.CASE_INSENSITIVE
    );

    private record ProjectResult(List<Commit> commits, String projectName, boolean isValid, ActiveProjectInfo activeInfo) {}

    @Override
    public SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        this.rateLimiter = new Semaphore(profile.sourceControlRateLimit());

        String baseUrl = profile.sourceControlURL().replaceAll("/+$", "");
        if (baseUrl.isBlank()) baseUrl = "https://gitlab.com";
        String apiUrl = baseUrl.endsWith("/api/v4") ? baseUrl : baseUrl + "/api/v4";
        String groupId = profile.sourceControlGroupId();
        String token = profile.sourceControlToken();

        GitLabClient client = RestClientBuilder.newBuilder()
                .baseUri(URI.create(apiUrl))
                .build(GitLabClient.class);

        System.out.println("[GITLAB] Fetching group info and projects for group ID: " + groupId);
        String groupName = client.getGroup(groupId, token).path("name").asText(groupId);

        List<JsonNode> allProjects = fetchAllProjects(client, groupId, token);
        List<String> ignoredByUserProjects = new ArrayList<>();
        List<JsonNode> projectsToAudit = new ArrayList<>();

        for (JsonNode repo : allProjects) {
            String repoName = repo.path("name").asText();
            if (profile.projectsToIgnore() != null && profile.projectsToIgnore().contains(repo.path("id").asLong())) {
                ignoredByUserProjects.add(repoName);
            } else {
                projectsToAudit.add(repo);
            }
        }

        List<Commit> allCommits = new ArrayList<>();
        List<ActiveProjectInfo> activeProjects = new ArrayList<>();
        List<String> missingBranchProjects = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<ProjectResult>> tasks = projectsToAudit.stream()
                    .map(repo -> (Callable<ProjectResult>) () -> processProject(client, repo, profile, sourceBranches, targetBranches))
                    .toList();

            executor.invokeAll(tasks).forEach(future -> {
                try {
                    ProjectResult pr = future.get();
                    allCommits.addAll(pr.commits());
                    if (!pr.isValid()) missingBranchProjects.add(pr.projectName());
                    else if (!pr.commits().isEmpty()) activeProjects.add(pr.activeInfo());
                } catch (Exception ignored) {}
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        SourceControlInfo scInfo = new SourceControlInfo("GitLab", baseUrl, groupName, groupId,
                allProjects.size(), (allProjects.size() - missingBranchProjects.size() - ignoredByUserProjects.size()), activeProjects.size());

        return new SourceControlResult(scInfo, allCommits, activeProjects, missingBranchProjects, ignoredByUserProjects);
    }

    private ProjectResult processProject(GitLabClient client, JsonNode repo, Profile profile, List<String> sourceBranches, List<String> targetBranches) throws InterruptedException {
        rateLimiter.acquire();
        try {
            String repoName = repo.path("name").asText();
            long repoId = repo.path("id").asLong();
            System.out.println("  [>] Auditing project: " + repoName);

            for (String target : targetBranches) {
                for (String source : sourceBranches) {
                    try {
                        JsonNode comparison = client.compareBranches(repoId, target, source, profile.sourceControlToken());
                        List<Commit> commits = processCommits(comparison.path("commits"), profile.taskRegex(), repoName);

                        ActiveProjectInfo activeInfo = commits.isEmpty() ? null : createActiveInfo(repoName, source, target, commits);
                        return new ProjectResult(commits, repoName, true, activeInfo);

                    } catch (ResourceNotFoundException e) {
                        continue; // Branch não encontrada, tenta a próxima combinação
                    }
                }
            }
            return new ProjectResult(List.of(), repoName, false, null);
        } finally {
            rateLimiter.release();
        }
    }

    private List<Commit> processCommits(JsonNode commitsArray, String regex, String repoName) {
        List<Commit> commits = new ArrayList<>();
        commitsArray.forEach(node -> {
            if (isMeaningfulCommit(node)) {
                commits.add(mapToDomainCommit(node, regex, repoName));
            }
        });
        return commits;
    }

    private ActiveProjectInfo createActiveInfo(String repoName, String source, String target, List<Commit> commits) {
        String lastDateStr = commits.stream()
                .map(Commit::createdAt)
                .filter(date -> !date.isBlank())
                .map(Instant::parse)
                .max(Instant::compareTo)
                .map(instant -> new SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date.from(instant)))
                .orElse("N/A");

        long uniqueTasksCount = commits.stream()
                .flatMap(c -> c.associatedTaskIds().stream())
                .distinct()
                .count();

        return new ActiveProjectInfo(
                repoName, source, target,
                String.valueOf(commits.size()), lastDateStr, String.valueOf(uniqueTasksCount)
        );
    }

    private List<JsonNode> fetchAllProjects(GitLabClient client, String groupId, String token) {
        List<JsonNode> all = new ArrayList<>();
        int page = 1;
        while (true) {
            List<JsonNode> projects = client.getGroupProjects(groupId, true, 100, page++, token);
            if (projects.isEmpty()) break;
            all.addAll(projects);
        }
        return all;
    }

    private boolean isMeaningfulCommit(JsonNode node) {
        String msg = node.path("message").asText("");
        return !msg.isBlank() && !IGNORE_COMMIT_PATTERN.matcher(msg.trim()).find();
    }

    private Commit mapToDomainCommit(JsonNode node, String regex, String projectName) {
        String message = node.path("message").asText();
        return new Commit(
                node.path("id").asText(),
                message,
                node.path("author_name").asText("Unknown"),
                projectName,
                extractTaskIds(message, regex),
                node.path("web_url").asText(),
                node.path("created_at").asText());
    }

    private List<String> extractTaskIds(String message, String regex) {
        List<String> ids = new ArrayList<>();
        if (message != null && regex != null) {
            Matcher m = Pattern.compile(regex).matcher(message);
            while (m.find()) ids.add(m.group());
        }
        return ids;
    }
}