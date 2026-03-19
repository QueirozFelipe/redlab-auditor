package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.ActiveProjectInfo;
import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.SourceControlInfo;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Alternative
@Priority(1)
public class MockSourceControlAdapter implements SourceControlPort {

    @Override
    public SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        System.out.println("[MOCK] Fetching 100 mocked commits with rich project data");

        List<Commit> commits = new ArrayList<>();
        String[] authors = {"Alice Smith", "Bob Jones", "Charlie Brown", "Eve Davis", "Dave Miller", "Felipe Queiroz", "System"};

        String[] projectNames = {
                "booking-engine-core",
                "admin-dashboard-ui",
                "payment-gateway-service",
                "notification-worker"
        };

        // Generating Valid Commits
        for (int i = 1; i <= 85; i++) {
            String hash = UUID.randomUUID().toString().substring(0, 8);
            String author = authors[i % 5];
            String taskId = String.valueOf(100000 + ((i % 25) + 1));
            String projectName = projectNames[i % projectNames.length]; // Distribui entre os 4 projetos

            commits.add(new Commit(
                    hash,
                    "Implement logic for task " + taskId + " (part " + i + ")",
                    author,
                    projectName, // Novo campo adicionado!
                    List.of(taskId),
                    "http://mock-gitlab/commit/" + hash
            ));
        }

        // Generating Orphan Commits
        for (int i = 1; i <= 15; i++) {
            String hash = UUID.randomUUID().toString().substring(0, 8);
            String author = (i % 4 == 0) ? "System" : authors[i % 5];
            String projectName = projectNames[i % projectNames.length];

            List<String> taskIds = (i % 2 == 0) ? List.of() : List.of("99999" + i);

            commits.add(new Commit(
                    hash,
                    "Orphan commit " + i + " - fixing typos or merging branches",
                    author,
                    projectName, // Novo campo adicionado!
                    taskIds,
                    "http://mock-gitlab/commit/" + hash
            ));
        }

        // Generating ActiveProjectInfo with mocked data
        List<ActiveProjectInfo> activeProjects = List.of(
                new ActiveProjectInfo("booking-engine-core", "dev", "main", "35", "2026-03-18 10:30", "12"),
                new ActiveProjectInfo("admin-dashboard-ui", "develop", "master", "25", "2026-03-17 14:15", "8"),
                new ActiveProjectInfo("payment-gateway-service", "feature/new-gateway", "main", "20", "2026-03-18 09:00", "5"),
                new ActiveProjectInfo("notification-worker", "dev", "main", "20", "2026-03-16 16:45", "4")
        );

        List<String> missingBranchProjects = List.of(
                "legacy-xml-parser",
                "poc-archived-app"
        );

        List<String> ignoredByUserProjects = List.of(
                "legacy-auth-service-v1",
                "sandbox-poc",
                "company-shared-assets",
                "infrastructure-as-code-bak"
        );

        SourceControlInfo scInfo = new SourceControlInfo("Mock", "http://mock-gitlab", "Group", "1", 6, 4, 4);

        return new SourceControlResult(scInfo, commits, activeProjects, missingBranchProjects, ignoredByUserProjects);
    }
}