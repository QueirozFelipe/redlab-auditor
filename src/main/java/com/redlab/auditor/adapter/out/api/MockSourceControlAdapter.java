package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.ActiveProjectInfo;
import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.SourceControlInfo;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Named("SC_MOCK")
public class MockSourceControlAdapter implements SourceControlPort {

    @Override
    public SourceControlResult compareBranches(Profile profile, List<String> sourceBranches, List<String> targetBranches) {
        System.out.println("[MOCK] Fetching 100 mocked commits with rich project data");

        List<Commit> commits = new ArrayList<>();

        String[] projectNames = {"booking-engine-core", "admin-dashboard-ui", "payment-gateway-service", "notification-worker"};

        for (int i = 0; i < 25; i++) {
            String hash = UUID.randomUUID().toString().substring(0, 8);
            commits.add(new Commit(hash, "Refactoring core logic for task 100001 - part " + i, "Felipe Queiroz", "booking-engine-core", List.of("100001"), "http://url/"+hash));
        }

        for (int i = 0; i < 12; i++) {
            String hash = UUID.randomUUID().toString().substring(0, 8);
            commits.add(new Commit(hash, "Fixing CSS for task 100005", "Alice Smith", "admin-dashboard-ui", List.of("100005"), "http://url/"+hash));
        }

        for (int i = 1; i <= 60; i++) {
            String hash = UUID.randomUUID().toString().substring(0, 8);

            double rand = Math.random();
            String author = (rand > 0.7) ? "Felipe Queiroz" : (rand > 0.5) ? "Alice Smith" : (rand > 0.3) ? "Bob Jones" : "Charlie Brown";

            String taskId = String.valueOf(100000 + (int)(Math.random() * 20 + 1));
            String projectName = projectNames[i % projectNames.length];

            commits.add(new Commit(hash, "Update " + hash, author, projectName, List.of(taskId), "http://url/"+hash));
        }

        for (int i = 1; i <= 20; i++) {
            String hash = UUID.randomUUID().toString().substring(0, 8);
            commits.add(new Commit(hash, "Merge branch 'main' into 'dev'", "System", "booking-engine-core", List.of(), "http://url/"+hash));
        }

        List<ActiveProjectInfo> activeProjects = List.of(
                new ActiveProjectInfo("booking-engine-core", "dev", "main", "45", "2026-03-21 22:00", "5"),
                new ActiveProjectInfo("admin-dashboard-ui", "develop", "master", "12", "2026-03-20 14:15", "8")
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