//package com.redlab.auditor.adapter.out.api;
//
//import com.redlab.auditor.domain.model.Commit;
//import com.redlab.auditor.domain.model.Profile;
//import com.redlab.auditor.usecase.port.out.SourceControlPort;
//import com.redlab.auditor.usecase.port.out.SourceControlResult;
//import jakarta.annotation.Priority;
//import jakarta.enterprise.context.ApplicationScoped;
//import jakarta.enterprise.inject.Alternative;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.UUID;
//
//@ApplicationScoped
//@Alternative
//@Priority(1)
//public class MockSourceControlAdapter implements SourceControlPort {
//
//    @Override
//    public SourceControlResult fetchCommitsSinceLastTag(Profile profile, String targetBranch) {
//        System.out.println("[MOCK] Fetching 100 mocked commits comparing " + targetBranch);
//
//        List<Commit> commits = new ArrayList<>();
//        String[] authors = {"Alice Smith", "Bob Jones", "Charlie Brown", "Eve Davis", "Dave Miller", "Felipe Queiroz", "System"};
//
//        for (int i = 1; i <= 85; i++) {
//            String hash = UUID.randomUUID().toString().substring(0, 8);
//            String author = authors[i % 5];
//            String taskId = String.valueOf(100000 + ((i % 25) + 1));
//
//            commits.add(new Commit(
//                    hash,
//                    "Implement logic for task " + taskId + " (part " + i + ")",
//                    author,
//                    List.of(taskId),
//                    "http://mock-gitlab/commit/" + hash
//            ));
//        }
//
//        for (int i = 1; i <= 15; i++) {
//            String hash = UUID.randomUUID().toString().substring(0, 8);
//            String author = (i % 4 == 0) ? "System" : authors[i % 5];
//
//            List<String> taskIds = (i % 2 == 0) ? List.of() : List.of("99999" + i);
//
//            commits.add(new Commit(
//                    hash,
//                    "Orphan commit " + i + " - fixing typos or merging branches",
//                    author,
//                    taskIds,
//                    "http://mock-gitlab/commit/" + hash
//            ));
//        }
//
//        List<String> activeProjects = List.of(
//                "booking-engine-core",
//                "admin-dashboard-ui",
//                "payment-gateway-service",
//                "notification-worker"
//        );
//
//        List<String> ignoredProjects = List.of(
//                "legacy-xml-parser",
//                "poc-archived-app"
//        );
//
//        return new SourceControlResult(commits, activeProjects, ignoredProjects);
//    }
//}