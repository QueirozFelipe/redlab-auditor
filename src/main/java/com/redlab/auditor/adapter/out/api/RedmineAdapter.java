package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerInfo;
import com.redlab.auditor.domain.model.Task;
import com.redlab.auditor.domain.model.Tracker;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.redlab.auditor.usecase.port.out.ProjectManagerResult;
import com.taskadapter.redmineapi.*;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Version;
import jakarta.enterprise.context.ApplicationScoped;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@ApplicationScoped
public class RedmineAdapter implements ProjectManagerPort {

    @Override
    public ProjectManagerResult fetchTasksByVersion(Profile profile, String version) {
        RedmineManager redmineManager = RedmineManagerFactory.createWithApiKey(
          profile.redmineUrl(),
          profile.redmineToken()
        );

        try {
            IssueManager issueManager = redmineManager.getIssueManager();
            ProjectManager projectManager = redmineManager.getProjectManager();

            Version targetVersion = projectManager.getVersionById(Integer.parseInt(version));

            String projectId = ofNullable(targetVersion.getProjectId()).map(Object::toString).orElse("");
            String projectName = ofNullable(targetVersion.getProjectName()).orElse("");
            String versionName = ofNullable(targetVersion.getName()).orElse("");
            String versionStatus = ofNullable(targetVersion.getStatus()).orElse("");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String dueDate = ofNullable(targetVersion.getDueDate()).map(sdf::format).orElse("");
            String versionUrl = profile.redmineUrl() + "/versions/" + version;

            List<Tracker> selectedTrackers = new ArrayList<>();
            if (profile.redmineTrackers() != null && !profile.redmineTrackers().isBlank()) {
                Set<String> trackerIds = Arrays.stream(profile.redmineTrackers().split(","))
                  .map(String::trim)
                  .collect(Collectors.toSet());

                List<com.taskadapter.redmineapi.bean.Tracker> allTrackers = issueManager.getTrackers();
                selectedTrackers = allTrackers.stream()
                  .filter(t -> trackerIds.contains(String.valueOf(t.getId())))
                  .map(t -> new Tracker(String.valueOf(t.getId()), t.getName()))
                  .collect(Collectors.toList());
            } else {
                selectedTrackers.add(new Tracker("*", "All Trackers"));
            }

            ProjectManagerInfo pmInfo = new ProjectManagerInfo(
              "Redmine",
              profile.redmineUrl(),
              projectName,
              projectId,
              versionName,
              version,
              versionUrl,
              versionStatus,
              dueDate,
              selectedTrackers
            );

            Map<String, String> parameters = new HashMap<>();
            parameters.put("fixed_version_id", version);
            parameters.put("status_id", "*");
            if (profile.redmineTrackers() != null && !profile.redmineTrackers().isBlank()) {
                parameters.put("tracker_id", profile.redmineTrackers());
            }

            List<Issue> issues = issueManager.getIssues(parameters).getResults();

            List<Task> tasks = issues.stream()
              .map(issue -> mapToDomainTask(issue, profile.redmineUrl()))
              .collect(Collectors.toList());

            return new ProjectManagerResult(pmInfo, tasks);

        } catch (RedmineException e) {
            throw new RuntimeException("Error fetching data from Redmine: " + e.getMessage(), e);
        }
    }

    private Task mapToDomainTask(Issue issue, String baseUrl) {
        String assigneeName = (issue.getAssigneeName() != null) ? issue.getAssigneeName() : "Unassigned";
        String statusName = (issue.getStatusName() != null) ? issue.getStatusName() : "Unknown";

        String taskUrl = baseUrl + "/issues/" + issue.getId();

        return new Task(
                String.valueOf(issue.getId()),
                issue.getSubject(),
                assigneeName,
                statusName,
                taskUrl
        );
    }
}
