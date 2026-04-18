package com.redlab.auditor.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.redlab.auditor.adapter.out.api.client.GitHubClient;
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
@Named("GITHUB")
public class GitHubAdapter implements SourceControlPort {

    private Semaphore rateLimiter;
    private static final String GITHUB_API_VERSION = "2022-11-28";
    private static final Pattern IGNORE_COMMIT_PATTERN = Pattern.compile(
            "^(Merge branch|Merge pull request|See merge request|Merge tag|chore:?\\s*release).*",
            Pattern.CASE_INSENSITIVE
    );

    private record ProjectResult(List<Commit> commits, String projectName, boolean isValid, ActiveProjectInfo activeInfo) {}

    @Override
    public SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        this.rateLimiter = new Semaphore(profile.sourceControlRateLimit());

        String owner = profile.sourceControlGroupId();
        String baseUrl = profile.sourceControlURL().replaceAll("/+$", "");
        if (baseUrl.isBlank()) baseUrl = "https://api.github.com";
        String token = "Bearer " + profile.sourceControlToken();

        GitHubClient client = RestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl))
                .build(GitHubClient.class);

        System.out.println("[GITHUB] Fetching repositories for owner: " + owner);
        List<JsonNode> allProjects = fetchAllRepositories(client, owner, token);

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
                    .map(repo -> (Callable<ProjectResult>) () -> processProject(client, owner, repo, profile, sourceBranches, targetBranches, token))
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

        SourceControlInfo scInfo = new SourceControlInfo("GitHub", baseUrl, owner, owner,
                allProjects.size(), (allProjects.size() - missingBranchProjects.size() - ignoredByUserProjects.size()), activeProjects.size());

        return new SourceControlResult(scInfo, allCommits, activeProjects, missingBranchProjects, ignoredByUserProjects);
    }

    private ProjectResult processProject(GitHubClient client, String owner, JsonNode repo, Profile profile, List<String> sourceBranches, List<String> targetBranches, String token) throws InterruptedException {
        rateLimiter.acquire();
        try {
            String repoName = repo.path("name").asText();
            System.out.println("  [>] Auditing project: " + repoName);

            for (String target : targetBranches) {
                for (String source : sourceBranches) {
                    try {
                        JsonNode comparison = client.compareBranches(owner, repoName, target, source, token, GITHUB_API_VERSION);
                        List<Commit> commits = processCommits(comparison.path("commits"), profile.taskRegex(), repoName);

                        ActiveProjectInfo activeInfo = commits.isEmpty() ? null : createActiveInfo(repoName, source, target, commits);
                        return new ProjectResult(commits, repoName, true, activeInfo);
                    } catch (ResourceNotFoundException e) {
                        continue;
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

        long uniqueTasksCount = commits.stream().flatMap(c -> c.associatedTaskIds().stream()).distinct().count();
        return new ActiveProjectInfo(repoName, source, target, String.valueOf(commits.size()), lastDateStr, String.valueOf(uniqueTasksCount));
    }

    private List<JsonNode> fetchAllRepositories(GitHubClient client, String owner, String token) {
        List<JsonNode> all = new ArrayList<>();
        int page = 1;
        boolean isOrg = true;

        while (true) {
            try {
                List<JsonNode> repos = isOrg
                        ? client.getOrgRepos(owner, 100, page++, token, GITHUB_API_VERSION)
                        : client.getUserRepos(owner, 100, page++, token, GITHUB_API_VERSION);

                if (repos.isEmpty()) break;
                all.addAll(repos);
            } catch (ResourceNotFoundException e) {
                if (isOrg && page == 1) {
                    isOrg = false;
                    continue;
                }
                throw e;
            }
        }
        return all;
    }

    private boolean isMeaningfulCommit(JsonNode node) {
        String msg = node.path("commit").path("message").asText("");
        return !msg.isBlank() && !IGNORE_COMMIT_PATTERN.matcher(msg.trim()).find();
    }

    private Commit mapToDomainCommit(JsonNode node, String regex, String projectName) {
        String message = node.path("commit").path("message").asText();
        String date = node.path("commit").path("committer").path("date").asText();
        return new Commit(node.path("sha").asText(), message, node.path("commit").path("author").path("name").asText("Unknown"),
                projectName, extractTaskIds(message, regex), node.path("html_url").asText(), date);
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