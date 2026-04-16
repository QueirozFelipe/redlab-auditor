package com.redlab.auditor.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redlab.auditor.domain.model.ActiveProjectInfo;
import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.SourceControlInfo;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Named("GITLAB")
public class GitLabAdapter implements SourceControlPort {

    @Inject
    ObjectMapper mapper;

    private final HttpClient httpClient;
    private Semaphore rateLimiter;

    private record ProjectResult(List<Commit> commits, String projectName, boolean isValid, ActiveProjectInfo activeInfo) {}

    private static final Pattern IGNORE_COMMIT_PATTERN = Pattern.compile(
            "^(Merge branch|Merge pull request|See merge request|Merge tag|chore:?\\s*release).*",
            Pattern.CASE_INSENSITIVE
    );

    public GitLabAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        this.rateLimiter = new Semaphore(profile.sourceControlRateLimit());

        String baseUrl = profile.sourceControlURL().replaceAll("/+$", "");
        if (baseUrl.isBlank()) baseUrl = "https://gitlab.com";
        String apiUrl = baseUrl.endsWith("/api/v4") ? baseUrl : baseUrl + "/api/v4";

        String groupId = profile.sourceControlGroupId();

        try {
            System.out.println("[GITLAB] Fetching group info and projects for group ID: " + groupId);

            JsonNode groupNode = fetchGroupInfo(apiUrl, groupId, profile.sourceControlToken());
            String groupName = groupNode.path("name").asText(groupId);

            List<JsonNode> allProjects = fetchAllProjects(apiUrl, groupId, profile.sourceControlToken());

            List<String> ignoredByUserProjects = new ArrayList<>();
            List<JsonNode> projectsToAudit = new ArrayList<>();

            for (JsonNode repo : allProjects) {
                long repoId = repo.path("id").asLong();
                String repoName = repo.path("name").asText();

                if (profile.projectsToIgnore() != null && profile.projectsToIgnore().contains(repoId)) {
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
                        .map(repo -> (Callable<ProjectResult>) () -> processProject(apiUrl, repo, profile, sourceBranches, targetBranches))
                        .toList();

                List<Future<ProjectResult>> results = executor.invokeAll(tasks);

                for (Future<ProjectResult> resultFuture : results) {
                    ProjectResult pr = resultFuture.get();

                    allCommits.addAll(pr.commits());

                    if (!pr.isValid()) {
                        missingBranchProjects.add(pr.projectName());
                    } else if (!pr.commits().isEmpty()) {
                        activeProjects.add(pr.activeInfo());
                    }
                }
            }

            int totalProjects = allProjects.size();
            int validProjectsCount = totalProjects - missingBranchProjects.size() - ignoredByUserProjects.size();

            SourceControlInfo scInfo = new SourceControlInfo(
                    "GitLab",
                    baseUrl,
                    groupName,
                    groupId,
                    totalProjects,
                    validProjectsCount,
                    activeProjects.size()
            );

            return new SourceControlResult(scInfo, allCommits, activeProjects, missingBranchProjects, ignoredByUserProjects);

        } catch (Exception e) {
            throw new RuntimeException("Error fetching data from GitLab: " + e.getMessage(), e);
        }
    }

    private ProjectResult processProject(String apiUrl, JsonNode repo, Profile profile, List<String> sourceBranches, List<String> targetBranches) throws InterruptedException {
        rateLimiter.acquire();
        String repoName = repo.path("name").asText();
        long repoId = repo.path("id").asLong();
        try {
            System.out.println("  [>] Auditing project: " + repoName);
            return executeWithFailover(apiUrl, repoId, repoName, profile, sourceBranches, targetBranches);
        } finally {
            rateLimiter.release();
        }
    }

    private ProjectResult executeWithFailover(String apiUrl, long repoId, String repoName, Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        for (String target : targetBranches) {
            for (String source : sourceBranches) {
                try {
                    String encodedTarget = URLEncoder.encode(target, StandardCharsets.UTF_8);
                    String encodedSource = URLEncoder.encode(source, StandardCharsets.UTF_8);

                    String compareUrl = String.format("%s/projects/%d/repository/compare?from=%s&to=%s",
                            apiUrl, repoId, encodedTarget, encodedSource);

                    HttpResponse<String> response = executeGet(compareUrl, profile.sourceControlToken());

                    if (response.statusCode() == 404) {
                        continue;
                    }
                    if (response.statusCode() >= 400) {
                        System.err.println("    [!] Error comparing " + target + "..." + source + " in " + repoName + ": HTTP " + response.statusCode());
                        break;
                    }

                    JsonNode comparison = mapper.readTree(response.body());
                    JsonNode commitsArray = comparison.path("commits");

                    List<Commit> commits = new ArrayList<>();
                    String lastDateStr = "N/A";
                    Date maxDate = new Date(0);

                    for (JsonNode commitNode : commitsArray) {
                        if (isMeaningfulCommit(commitNode)) {
                            Commit domainCommit = mapToDomainCommit(commitNode, profile.taskRegex(), repoName);
                            commits.add(domainCommit);

                            String dateIso = commitNode.path("created_at").asText();
                            if (!dateIso.isBlank()) {
                                Date commitDate = Date.from(Instant.parse(dateIso));
                                if (commitDate.after(maxDate)) {
                                    maxDate = commitDate;
                                    lastDateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(commitDate);
                                }
                            }
                        }
                    }

                    ActiveProjectInfo activeInfo = null;

                    if (!commits.isEmpty()) {
                        long uniqueTasksCount = commits.stream()
                                .flatMap(c -> c.associatedTaskIds().stream())
                                .distinct()
                                .count();

                        activeInfo = new ActiveProjectInfo(
                                repoName, source, target,
                                String.valueOf(commits.size()), lastDateStr, String.valueOf(uniqueTasksCount)
                        );
                    }

                    return new ProjectResult(commits, repoName, true, activeInfo);

                } catch (Exception e) {
                    System.err.println("    [!] Error processing " + repoName + ": " + e.getMessage());
                    break;
                }
            }
        }

        return new ProjectResult(List.of(), repoName, false, null);
    }

    private JsonNode fetchGroupInfo(String apiUrl, String groupId, String token) throws Exception {
        String url = apiUrl + "/groups/" + groupId;
        HttpResponse<String> response = executeGet(url, token);
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Failed to fetch group info. HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private List<JsonNode> fetchAllProjects(String apiUrl, String groupId, String token) throws Exception {
        List<JsonNode> allProjects = new ArrayList<>();
        int page = 1;

        while (true) {
            String pagedUrl = String.format("%s/groups/%s/projects?include_subgroups=true&per_page=100&page=%d", apiUrl, groupId, page);
            HttpResponse<String> response = executeGet(pagedUrl, token);

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Failed to fetch repositories. HTTP " + response.statusCode());
            }

            JsonNode reposArray = mapper.readTree(response.body());
            if (reposArray.isEmpty()) {
                break;
            }

            for (JsonNode repo : reposArray) {
                allProjects.add(repo);
            }
            page++;
        }
        return allProjects;
    }

    private HttpResponse<String> executeGet(String url, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("PRIVATE-TOKEN", token)
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isMeaningfulCommit(JsonNode commitNode) {
        String message = commitNode.path("message").asText("");
        if (message.isBlank()) return false;
        return !IGNORE_COMMIT_PATTERN.matcher(message.trim()).find();
    }

    private Commit mapToDomainCommit(JsonNode commitNode, String regex, String projectName) {
        String hash = commitNode.path("id").asText(); // No GitLab o hash principal se chama 'id'
        String message = commitNode.path("message").asText();
        String author = commitNode.path("author_name").asText("Unknown");
        String url = commitNode.path("web_url").asText();

        List<String> associatedTasks = extractTaskIdsFromMessage(message, regex);

        return new Commit(hash, message, author, projectName, associatedTasks, url);
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