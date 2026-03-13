//package com.redlab.auditor.adapter.out.api;
//
//import com.redlab.auditor.domain.model.Task;
//import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
//import jakarta.annotation.Priority;
//import jakarta.enterprise.context.ApplicationScoped;
//import jakarta.enterprise.inject.Alternative;
//
//import java.util.List;
//
//@ApplicationScoped
//@Alternative
//@Priority(1)
//public class MockProjectManagerAdapter implements ProjectManagerPort {
//
//    @Override
//    public List<Task> fetchTasksByVersion(String version) {
//        System.out.println("[MOCK] Fetching 10 mocked tasks for version: " + version);
//
//        return List.of(
//                new Task("100001", "Fix authentication bypass vulnerability", "Alice Smith", "Closed", "http://mock-redmine/issues/100001"),
//                new Task("100002", "Implement payment gateway retry logic", "Bob Jones", "In Progress", "http://mock-redmine/issues/100002"),
//                new Task("100003", "Update Spring Boot dependencies", "Charlie Brown", "Resolved", "http://mock-redmine/issues/100003"),
//                new Task("100004", "Refactor user profile UI components", "Eve Davis", "Resolved", "http://mock-redmine/issues/100004"),
//                new Task("100005", "Migrate legacy user data to new schema", "Dave Miller", "Closed", "http://mock-redmine/issues/100005"),
//                new Task("100006", "Resolve email notification delivery failure", "Alice Smith", "In Progress", "http://mock-redmine/issues/100006"),
//                new Task("100007", "Implement API rate limiting mechanism", "Bob Jones", "Resolved", "http://mock-redmine/issues/100007"),
//                new Task("100008", "Investigate Redis cache invalidation lag", "Charlie Brown", "New", "http://mock-redmine/issues/100008"), // Tarefa sem commits propositalmente
//                new Task("100009", "Draft SSO integration documentation", "Eve Davis", "New", "http://mock-redmine/issues/100009"), // Tarefa sem commits propositalmente
//                new Task("100010", "Adjust logging format for ELK stack", "Dave Miller", "Closed", "http://mock-redmine/issues/100010")
//        );
//    }
//}