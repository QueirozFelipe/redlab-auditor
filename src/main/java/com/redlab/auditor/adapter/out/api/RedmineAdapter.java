package com.redlab.auditor.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.redlab.auditor.adapter.out.api.client.RedmineClient;
import com.redlab.auditor.domain.exception.ResourceNotFoundException;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerInfo;
import com.redlab.auditor.domain.model.Task;
import com.redlab.auditor.domain.model.Tracker;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.redlab.auditor.usecase.port.out.ProjectManagerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Named("REDMINE")
public class RedmineAdapter implements ProjectManagerPort {

    @Override
    public ProjectManagerResult fetchTasksByVersion(Profile profile, String version) {
        String baseUrl = profile.projectManagerURL().replaceAll("/+$", "");
        String token = profile.projectManagerToken();

        RedmineClient redmineClient = RestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl))
                .build(RedmineClient.class);

        JsonNode versionNode = redmineClient.getVersion(version, token).path("version");

        String projectId = versionNode.path("project").path("id").asText("");
        String projectName = versionNode.path("project").path("name").asText("");
        String versionName = versionNode.path("name").asText("");
        String versionStatus = versionNode.path("status").asText("");
        String dueDate = versionNode.path("due_date").asText("");
        String versionUrl = String.format("%s/versions/%s", baseUrl, version);

        List<Tracker> selectedTrackers = fetchTrackers(redmineClient, token, profile.projectManagerIssueTypes());

        ProjectManagerInfo pmInfo = new ProjectManagerInfo(
                "Redmine", baseUrl, projectName, projectId, versionName,
                version, versionUrl, versionStatus, dueDate, selectedTrackers
        );

        List<Task> tasks = fetchAllTasks(redmineClient, token, version, selectedTrackers, baseUrl);

        return new ProjectManagerResult(pmInfo, tasks);
    }

    private List<Task> fetchAllTasks(RedmineClient client, String token, String versionId, List<Tracker> selectedTrackers, String baseUrl) {
        List<Task> allTasks = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        boolean hasMore = true;

        String trackerParam = (selectedTrackers.isEmpty() || selectedTrackers.getFirst().id().equals("*")) ? ""
                : selectedTrackers.stream().map(Tracker::id).collect(Collectors.joining(","));

        while (hasMore) {
            JsonNode response = client.getIssues(versionId, "*", limit, offset, trackerParam, token);
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

    private List<Tracker> fetchTrackers(RedmineClient client, String token, Set<Long> configIds) {
        if (configIds == null || configIds.isEmpty()) {
            return List.of(new Tracker("*", "All Trackers"));
        }

        List<Tracker> selectedTrackers = new ArrayList<>();
        JsonNode trackersArray = client.getTrackers(token).path("trackers");

        for (JsonNode typeNode : trackersArray) {
            long id = typeNode.path("id").asLong();
            if (configIds.contains(id)) {
                selectedTrackers.add(new Tracker(String.valueOf(id), typeNode.path("name").asText()));
            }
        }

        if (selectedTrackers.isEmpty()) {
            throw new ResourceNotFoundException("[ERROR] None of the configured Trackers " + configIds + " were found in Redmine.", null);
        }

        return selectedTrackers;
    }

    private Task mapToDomainTask(JsonNode issueNode, String baseUrl) {
        String id = issueNode.path("id").asText();
        return new Task(
                id,
                issueNode.path("subject").asText("No subject"),
                issueNode.path("assigned_to").path("name").asText("Unassigned"),
                issueNode.path("status").path("name").asText("Unknown"),
                String.format("%s/issues/%s", baseUrl, id)
        );
    }
}