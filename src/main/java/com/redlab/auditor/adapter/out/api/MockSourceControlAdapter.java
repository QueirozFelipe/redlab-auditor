package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.model.Commit;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;

@ApplicationScoped
@Alternative
@Priority(1)
public class MockSourceControlAdapter implements SourceControlPort {

    @Override
    public List<Commit> fetchCommitsSinceLastTag(String productionBranch, String targetBranch) {
        System.out.println("[MOCK] Fetching 30 mocked commits comparing " + productionBranch + " and " + targetBranch);

        return List.of(
                // Commits para a Tarefa 100001 (3 commits)
                new Commit("a1b2c3d4", "Initial fix for auth issue 100001", "Alice Smith", List.of("100001"), "http://mock-gitlab/commit/a1b2c3d4"),
                new Commit("b2c3d4e5", "Add unit tests for 100001", "Alice Smith", List.of("100001"), "http://mock-gitlab/commit/b2c3d4e5"),
                new Commit("c3d4e5f6", "Ref 100001: code review adjustments", "Alice Smith", List.of("100001"), "http://mock-gitlab/commit/c3d4e5f6"),

                // Commits para a Tarefa 100002 (4 commits)
                new Commit("d4e5f6g7", "WIP: gateway 100002 structure", "Bob Jones", List.of("100002"), "http://mock-gitlab/commit/d4e5f6g7"),
                new Commit("e5f6g7h8", "Implement retry logic for 100002", "Bob Jones", List.of("100002"), "http://mock-gitlab/commit/e5f6g7h8"),
                new Commit("f6g7h8i9", "Fixing timeout bug on 100002", "Bob Jones", List.of("100002"), "http://mock-gitlab/commit/f6g7h8i9"),
                new Commit("g7h8i9j0", "Finalizing 100002 integration", "Bob Jones", List.of("100002"), "http://mock-gitlab/commit/g7h8i9j0"),

                // Commits para a Tarefa 100003 (2 commits)
                new Commit("h8i9j0k1", "Bump Spring Boot version 100003", "Charlie Brown", List.of("100003"), "http://mock-gitlab/commit/h8i9j0k1"),
                new Commit("i9j0k1l2", "Fix compilation errors after 100003 update", "Charlie Brown", List.of("100003"), "http://mock-gitlab/commit/i9j0k1l2"),

                // Commits para a Tarefa 100004 (5 commits)
                new Commit("j0k1l2m3", "Start UI revamp 100004", "Eve Davis", List.of("100004"), "http://mock-gitlab/commit/j0k1l2m3"),
                new Commit("k1l2m3n4", "Update CSS variables for 100004", "Eve Davis", List.of("100004"), "http://mock-gitlab/commit/k1l2m3n4"),
                new Commit("l2m3n4o5", "Ref 100004: add new components", "Eve Davis", List.of("100004"), "http://mock-gitlab/commit/l2m3n4o5"),
                new Commit("m3n4o5p6", "Fix responsive layout 100004", "Dave Miller", List.of("100004"), "http://mock-gitlab/commit/m3n4o5p6"),
                new Commit("n4o5p6q7", "Apply design review feedback 100004", "Eve Davis", List.of("100004"), "http://mock-gitlab/commit/n4o5p6q7"),

                // Commits para a Tarefa 100005 (3 commits)
                new Commit("o5p6q7r8", "Create migration script 100005", "Dave Miller", List.of("100005"), "http://mock-gitlab/commit/o5p6q7r8"),
                new Commit("p6q7r8s9", "Test migration on staging 100005", "Dave Miller", List.of("100005"), "http://mock-gitlab/commit/p6q7r8s9"),
                new Commit("q7r8s9t0", "Optimize query performance 100005", "Dave Miller", List.of("100005"), "http://mock-gitlab/commit/q7r8s9t0"),

                // Commits para a Tarefa 100006 (2 commits)
                new Commit("r8s9t0u1", "Investigate email issue 100006", "Alice Smith", List.of("100006"), "http://mock-gitlab/commit/r8s9t0u1"),
                new Commit("s9t0u1v2", "Apply patch for SMTP server 100006", "Alice Smith", List.of("100006"), "http://mock-gitlab/commit/s9t0u1v2"),

                // Commits para a Tarefa 100007 (4 commits)
                new Commit("t0u1v2w3", "Setup rate limiting framework 100007", "Bob Jones", List.of("100007"), "http://mock-gitlab/commit/t0u1v2w3"),
                new Commit("u1v2w3x4", "Configure endpoints for 100007", "Bob Jones", List.of("100007"), "http://mock-gitlab/commit/u1v2w3x4"),
                new Commit("v2w3x4y5", "Add interceptors Ref 100007", "Bob Jones", List.of("100007"), "http://mock-gitlab/commit/v2w3x4y5"),
                new Commit("w3x4y5z6", "Code review adjustments 100007", "Alice Smith", List.of("100007"), "http://mock-gitlab/commit/w3x4y5z6"),

                // Commits para a Tarefa 100010 (2 commits)
                new Commit("x4y5z6a7", "Update logback.xml for ELK 100010", "Dave Miller", List.of("100010"), "http://mock-gitlab/commit/x4y5z6a7"),
                new Commit("y5z6a7b8", "Add JSON formatting to logs 100010", "Dave Miller", List.of("100010"), "http://mock-gitlab/commit/y5z6a7b8"),

                // COMMITS ÓRFÃOS (5 commits simulando anomalias)
                new Commit("z6a7b8c9", "Merge branch 'hotfix-urgent' into dev", "System", List.of(), "http://mock-gitlab/commit/z6a7b8c9"),
                new Commit("a7b8c9d0", "Fixing typo in README", "Charlie Brown", List.of(), "http://mock-gitlab/commit/a7b8c9d0"),
                new Commit("b8c9d0e1", "Updating internal documentation", "Eve Davis", List.of(), "http://mock-gitlab/commit/b8c9d0e1"),
                new Commit("c9d0e1f2", "Refactor core module and clean up unused imports", "Dave Miller", List.of(), "http://mock-gitlab/commit/c9d0e1f2"),
                new Commit("d0e1f2g3", "Accidental commit with wrong task ID 999999", "Bob Jones", List.of("999999"), "http://mock-gitlab/commit/d0e1f2g3")
        );
    }
}