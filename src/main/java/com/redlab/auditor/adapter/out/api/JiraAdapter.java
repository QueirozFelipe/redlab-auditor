package com.redlab.auditor.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerInfo;
import com.redlab.auditor.domain.model.Task;
import com.redlab.auditor.domain.model.Tracker;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.redlab.auditor.usecase.port.out.ProjectManagerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Named("JIRA")
public class JiraAdapter implements ProjectManagerPort {

    @Inject
    ObjectMapper mapper;

    private final HttpClient httpClient;

    public JiraAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ProjectManagerResult fetchTasksByVersion(Profile profile, String version) {
        String baseUrl = profile.projectManagerURL().replaceAll("/+$", "");
        String authHeader = getBasicAuthHeader(profile.projectManagerToken());

        try {
            JsonNode versionNode = get(baseUrl + "/rest/api/3/version/" + version, authHeader);

            String projectId = versionNode.path("projectId").asText("");
            String versionName = versionNode.path("name").asText("");
            String versionStatus = versionNode.path("released").asBoolean() ? "Released" : "Unreleased";
            String dueDate = versionNode.path("releaseDate").asText("");
            String versionUrl = String.format("%s/projects/%s/versions/%s", baseUrl, projectId, version);

            String projectKey = getProjectKey(baseUrl, projectId, authHeader);

            List<Tracker> selectedTrackers = fetchTrackers(baseUrl, authHeader, profile.projectManagerIssueTypes());

            ProjectManagerInfo pmInfo = new ProjectManagerInfo(
                    "Jira", baseUrl, projectKey, projectId, versionName,
                    version, versionUrl, versionStatus, dueDate, selectedTrackers
            );

            String jql = buildJql(version, selectedTrackers);
            List<Task> tasks = fetchAllTasks(baseUrl, authHeader, jql);

            return new ProjectManagerResult(pmInfo, tasks);

        } catch (Exception e) {
            throw new RuntimeException("Error fetching data from Jira: " + e.getMessage(), e);
        }
    }

    private List<Task> fetchAllTasks(String baseUrl, String authHeader, String jql) throws Exception {
        List<Task> allTasks = new ArrayList<>();
        int startAt = 0;
        int maxResults = 100;
        boolean hasMore = true;
        String fields = "key,summary,assignee,status";
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);

        while (hasMore) {
            String searchUrl = String.format(
                    "%s/rest/api/3/search/jql?maxResults=%d&startAt=%d&fields=%s&jql=%s",
                    baseUrl, maxResults, startAt, fields, encodedJql
            );

            JsonNode response = get(searchUrl, authHeader);
            JsonNode issues = response.path("issues");

            if (issues.isArray() && !issues.isEmpty()) {
                for (JsonNode issue : issues) {
                    allTasks.add(mapToDomainTask(issue, baseUrl));
                }

                int total = response.path("total").asInt();
                startAt += issues.size();
                hasMore = startAt < total;

                System.out.printf("[JIRA] Fetched %d/%d issues...%n", startAt, total);
            } else {
                hasMore = false;
            }
        }
        return allTasks;
    }

    private List<Tracker> fetchTrackers(String baseUrl, String authHeader, Set<Long> configIds) throws Exception {
        List<Tracker> selectedTrackers = new ArrayList<>();
        if (configIds == null || configIds.isEmpty()) {
            selectedTrackers.add(new Tracker("*", "All Issue Types"));
            return selectedTrackers;
        }

        JsonNode issueTypesNode = get(baseUrl + "/rest/api/3/issuetype", authHeader);
        for (JsonNode typeNode : issueTypesNode) {
            long id = typeNode.path("id").asLong();
            if (configIds.contains(id)) {
                selectedTrackers.add(new Tracker(String.valueOf(id), typeNode.path("name").asText()));
            }
        }
        return selectedTrackers;
    }

    private String buildJql(String versionId, List<Tracker> selectedTrackers) {
        StringBuilder jql = new StringBuilder("fixVersion = ").append(versionId);

        if (!selectedTrackers.isEmpty() && !selectedTrackers.get(0).id().equals("*")) {
            String types = selectedTrackers.stream()
                    .map(Tracker::id)
                    .collect(Collectors.joining(","));
            jql.append(" AND issuetype in (").append(types).append(")");
        }
        return jql.toString();
    }

    private Task mapToDomainTask(JsonNode issueNode, String baseUrl) {
        String key = issueNode.path("key").asText();
        JsonNode fields = issueNode.path("fields");

        String summary = fields.path("summary").asText("No summary");
        String assignee = fields.path("assignee").path("displayName").asText("Unassigned");
        String status = fields.path("status").path("name").asText("Unknown");
        String url = String.format("%s/browse/%s", baseUrl, key);

        return new Task(key, summary, assignee, status, url);
    }

    private JsonNode get(String url, String authHeader) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException(String.format("HTTP %d - %s", response.statusCode(), response.body()));
        }
        return mapper.readTree(response.body());
    }

    private String getProjectKey(String baseUrl, String projectId, String authHeader) {
        try {
            JsonNode node = get(baseUrl + "/rest/api/3/project/" + projectId, authHeader);
            return node.path("key").asText(projectId);
        } catch (Exception e) {
            return projectId;
        }
    }

    private String getBasicAuthHeader(String token) {
        if (token.startsWith("Bearer ") || token.startsWith("Basic ")) {
            return token;
        }
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes());
    }
}