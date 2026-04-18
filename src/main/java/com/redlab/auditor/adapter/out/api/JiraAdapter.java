package com.redlab.auditor.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.redlab.auditor.adapter.out.api.client.JiraClient;
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
@Named("JIRA")
public class JiraAdapter implements ProjectManagerPort {

    @Override
    public ProjectManagerResult fetchTasksByVersion(Profile profile, String version) {
        String baseUrl = profile.projectManagerURL().replaceAll("/+$", "");
        String authHeader = getBasicAuthHeader(profile.projectManagerToken());

        JiraClient jiraClient = RestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl))
                .build(JiraClient.class);

        JsonNode versionNode = jiraClient.getVersion(version, authHeader);
        String projectId = versionNode.path("projectId").asText("");
        String versionName = versionNode.path("name").asText("");
        String versionStatus = versionNode.path("released").asBoolean() ? "Released" : "Unreleased";
        String dueDate = versionNode.path("releaseDate").asText("");
        String versionUrl = String.format("%s/projects/%s/versions/%s", baseUrl, projectId, version);
        String projectKey = getProjectKey(jiraClient, projectId, authHeader);

        List<Tracker> selectedTrackers = fetchTrackers(jiraClient, authHeader, profile.projectManagerIssueTypes());

        ProjectManagerInfo pmInfo = new ProjectManagerInfo(
                "Jira", baseUrl, projectKey, projectId, versionName,
                version, versionUrl, versionStatus, dueDate, selectedTrackers
        );

        String jql = buildJql(version, selectedTrackers);
        List<Task> tasks = fetchAllTasks(jiraClient, authHeader, jql, baseUrl);

        return new ProjectManagerResult(pmInfo, tasks);
    }

    private List<Task> fetchAllTasks(JiraClient client, String auth, String jql, String baseUrl) {
        List<Task> allTasks = new ArrayList<>();
        int startAt = 0;
        int maxResults = 100;
        boolean hasMore = true;
        String fields = "key,summary,assignee,status";

        while (hasMore) {
            JsonNode response = client.searchIssues(jql, startAt, maxResults, fields, auth);
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

    private List<Tracker> fetchTrackers(JiraClient client, String auth, Set<Long> configIds) {
        if (configIds == null || configIds.isEmpty()) {
            return List.of(new Tracker("*", "All Issue Types"));
        }

        List<Tracker> selected = new ArrayList<>();
        JsonNode typesNode = client.getIssueTypes(auth);

        for (JsonNode typeNode : typesNode) {
            long id = typeNode.path("id").asLong();
            if (configIds.contains(id)) {
                selected.add(new Tracker(String.valueOf(id), typeNode.path("name").asText()));
            }
        }
        return selected;
    }

    private String buildJql(String versionId, List<Tracker> selectedTrackers) {
        StringBuilder jql = new StringBuilder("fixVersion = ").append(versionId);
        if (!selectedTrackers.isEmpty() && !selectedTrackers.getFirst().id().equals("*")) {
            String types = selectedTrackers.stream().map(Tracker::id).collect(Collectors.joining(","));
            jql.append(" AND issuetype in (").append(types).append(")");
        }
        return jql.toString();
    }

    private Task mapToDomainTask(JsonNode issueNode, String baseUrl) {
        String key = issueNode.path("key").asText();
        JsonNode fields = issueNode.path("fields");
        return new Task(
                key,
                fields.path("summary").asText("No summary"),
                fields.path("assignee").path("displayName").asText("Unassigned"),
                fields.path("status").path("name").asText("Unknown"),
                String.format("%s/browse/%s", baseUrl, key)
        );
    }

    private String getProjectKey(JiraClient client, String projectId, String auth) {
        try {
            return client.getProject(projectId, auth).path("key").asText(projectId);
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