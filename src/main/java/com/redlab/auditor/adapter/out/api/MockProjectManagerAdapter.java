package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerInfo;
import com.redlab.auditor.domain.model.Task;
import com.redlab.auditor.domain.model.Tracker;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.redlab.auditor.usecase.port.out.ProjectManagerResult;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Alternative
@Priority(1)
public class MockProjectManagerAdapter implements ProjectManagerPort {

    @Override
    public ProjectManagerResult fetchTasksByVersion(Profile profile, String version) {
        System.out.println("[MOCK] Fetching 30 mocked tasks for version: " + version);

        List<Task> tasks = new ArrayList<>();
        String[] assignees = {"Alice Smith", "Bob Jones", "Charlie Brown", "Eve Davis", "Dave Miller", "Felipe Queiroz"};
        String[] statuses = {"Resolved", "Closed", "In Progress", "New"};

        for (int i = 1; i <= 30; i++) {
            String id = String.valueOf(100000 + i);
            String assignee = assignees[i % assignees.length];

            String status = (i > 25) ? "New" : statuses[i % statuses.length];

            tasks.add(new Task(
                    id,
                    "Mocked feature request or bugfix #" + id,
                    assignee,
                    status,
                    "http://mock-redmine/issues/" + id
            ));
        }

        return new ProjectManagerResult(
                new ProjectManagerInfo(
                        "Mock",
                        "http://mock-redmine",
                        "Project",
                        "1",
                        "release_1_0_0",
                        "1",
                        "http://mock-redmine/versions/1",
                        "Pending",
                        "2026-12-31",
                        List.of(new Tracker("1", "Task"))
                ),
                tasks
        );
    }
}