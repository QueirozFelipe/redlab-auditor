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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Named("REDMINE")
public class RedmineAdapter implements ProjectManagerPort {

    @Inject
    ObjectMapper mapper;

    private final HttpClient httpClient;

    public RedmineAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ProjectManagerResult fetchTasksByVersion(Profile profile, String version) {
        String baseUrl = profile.projectManagerURL().replaceAll("/+$", "");
        String token = profile.projectManagerToken();

        try {
            JsonNode versionNodeResponse = get(baseUrl + "/versions/" + version + ".json", token);
            JsonNode versionNode = versionNodeResponse.path("version");

            String projectId = versionNode.path("project").path("id").asText("");
            String projectName = versionNode.path("project").path("name").asText("");
            String versionName = versionNode.path("name").asText("");
            String versionStatus = versionNode.path("status").asText("");
            String dueDate = versionNode.path("due_date").asText("");
            String versionUrl = String.format("%s/versions/%s", baseUrl, version);

            List<Tracker> selectedTrackers = fetchTrackers(baseUrl, token, profile.projectManagerIssueTypes());

            ProjectManagerInfo pmInfo = new ProjectManagerInfo(
                    "Redmine", baseUrl, projectName, projectId, versionName,
                    version, versionUrl, versionStatus, dueDate, selectedTrackers
            );

            List<Task> tasks = fetchAllTasks(baseUrl, token, version, selectedTrackers);

            return new ProjectManagerResult(pmInfo, tasks);

        } catch (Exception e) {
            throw new RuntimeException("Error fetching data from Redmine: " + e.getMessage(), e);
        }
    }

    private List<Task> fetchAllTasks(String baseUrl, String token, String versionId, List<Tracker> selectedTrackers) throws Exception {
        List<Task> allTasks = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        boolean hasMore = true;

        String trackerParam = "";
        if (!selectedTrackers.isEmpty() && !selectedTrackers.get(0).id().equals("*")) {
            trackerParam = "&tracker_id=" + selectedTrackers.stream()
                    .map(Tracker::id)
                    .collect(Collectors.joining(","));
        }

        while (hasMore) {
            String searchUrl = String.format(
                    "%s/issues.json?fixed_version_id=%s&status_id=*&limit=%d&offset=%d%s",
                    baseUrl, versionId, limit, offset, trackerParam
            );

            JsonNode response = get(searchUrl, token);
            JsonNode issues = response.path("issues");

            if (issues.isArray() && !issues.isEmpty()) {
                for (JsonNode issue : issues) {
                    allTasks.add(mapToDomainTask(issue, baseUrl));
                }

                int totalCount = response.path("total_count").asInt();
                offset += issues.size();
                hasMore = offset < totalCount;

                System.out.printf("[REDMINE] Fetched %d/%d issues...%n", offset, totalCount);
            } else {
                hasMore = false;
            }
        }
        return allTasks;
    }

    private List<Tracker> fetchTrackers(String baseUrl, String token, Set<Long> configIds) throws Exception {
        List<Tracker> selectedTrackers = new ArrayList<>();
        if (configIds == null || configIds.isEmpty()) {
            selectedTrackers.add(new Tracker("*", "All Trackers"));
            return selectedTrackers;
        }

        JsonNode trackersResponse = get(baseUrl + "/trackers.json", token);
        JsonNode trackersArray = trackersResponse.path("trackers");

        for (JsonNode typeNode : trackersArray) {
            long id = typeNode.path("id").asLong();
            if (configIds.contains(id)) {
                selectedTrackers.add(new Tracker(String.valueOf(id), typeNode.path("name").asText()));
            }
        }

        if (selectedTrackers.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "[ERROR] None of the configured Trackers %s were found in Redmine. Please check your profile IDs.", configIds
            ));
        }

        return selectedTrackers;
    }

    private Task mapToDomainTask(JsonNode issueNode, String baseUrl) {
        String id = issueNode.path("id").asText();
        String subject = issueNode.path("subject").asText("No subject");

        String assignee = issueNode.path("assigned_to").path("name").asText("Unassigned");
        String status = issueNode.path("status").path("name").asText("Unknown");

        String url = String.format("%s/issues/%s", baseUrl, id);

        return new Task(id, subject, assignee, status, url);
    }

    private JsonNode get(String url, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Redmine-API-Key", token)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException(String.format("HTTP %d - %s", response.statusCode(), response.body()));
        }
        return mapper.readTree(response.body());
    }
}