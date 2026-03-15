package com.redlab.auditor.domain.model;

import java.util.List;
import java.util.Map;

public record AuditReport(
        String targetVersion,
        List<AuditReportItem> items,
        List<Commit> orphanCommits,
        List<String> activeProjects,
        List<String> ignoredProjects,
        long totalLinkedCommits,
        long tasksWithCommitCount,
        long tasksMissingCommitCount,
        Map<String, Long> commitsPerAuthor,
        Map<String, Long> tasksPerAssignee
) {
}
