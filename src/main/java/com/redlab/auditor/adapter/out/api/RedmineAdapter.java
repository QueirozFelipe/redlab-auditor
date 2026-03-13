package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Task;
import com.redlab.auditor.infrastructure.config.RedLabAuditorConfig;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineManagerFactory;
import com.taskadapter.redmineapi.bean.Issue;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class RedmineAdapter implements ProjectManagerPort {

    private final RedLabAuditorConfig config;
    private RedmineManager redmineManager;

    @Inject
    public RedmineAdapter(RedLabAuditorConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void initialize() {
        this.redmineManager = RedmineManagerFactory.createWithApiKey(
                config.redmine().url(),
                config.redmine().apiKey()
        );
    }

    @Override
    public List<Task> fetchTasksByVersion(String version) {
        try {
            IssueManager issueManager = redmineManager.getIssueManager();

            Map<String, String> parameters = new HashMap<>();

            parameters.put("fixed_version_id", version);
            parameters.put("status_id", "*");

            List<Issue> issues = issueManager.getIssues(parameters).getResults();

            return issues.stream()
                    .map(this::mapToDomainTask)
                    .collect(Collectors.toList());

        } catch (RedmineException e) {
            // Futuramente lançar uma exceção de domínio aqui (ex: AuditException)
            throw new RuntimeException("Error fetching tasks from Redmine: " + e.getMessage(), e);
        }
    }

    private Task mapToDomainTask(Issue issue) {
        String assigneeName = (issue.getAssigneeName() != null) ? issue.getAssigneeName() : "Unassigned";
        String statusName = (issue.getStatusName() != null) ? issue.getStatusName() : "Unknown";

        String taskUrl = config.redmine().url() + "/issues/" + issue.getId();

        return new Task(
                String.valueOf(issue.getId()),
                issue.getSubject(),
                assigneeName,
                statusName,
                taskUrl
        );
    }
}
