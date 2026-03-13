package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.Task;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class RedmineAdapter implements ProjectManagerPort {

    @Override
    public List<Task> fetchTasksByVersion(Profile profile, String version) {
        RedmineManager redmineManager = RedmineManagerFactory.createWithApiKey(
          profile.redmineUrl(),
          profile.redmineToken()
        );

        try {
            IssueManager issueManager = redmineManager.getIssueManager();

            Map<String, String> parameters = new HashMap<>();

            parameters.put("fixed_version_id", version);
            parameters.put("status_id", "*");
            if (profile.redmineTrackers() != null && !profile.redmineTrackers().isBlank()) {
                parameters.put("tracker_id", profile.redmineTrackers());
            }

            List<Issue> issues = issueManager.getIssues(parameters).getResults();

            return issues.stream()
                    .map(issue -> mapToDomainTask(issue, profile.redmineUrl()))
                    .collect(Collectors.toList());

        } catch (RedmineException e) {
            // Futuramente lançar uma exceção de domínio aqui (ex: AuditException)
            throw new RuntimeException("Error fetching tasks from Redmine: " + e.getMessage(), e);
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
