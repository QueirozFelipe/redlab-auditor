package com.redlab.auditor.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AuditReport(
        String toolVersion,
        String targetVersion,
        ProjectManagerInfo pmInfo,
        SourceControlInfo scInfo,
        List<AuditReportItem> items,
        long totalCommitsCount,
        List<Commit> orphanCommits,
        List<ActiveProjectInfo> activeProjects,
        List<String> missingBranchProjects,
        List<String> ignoredByUserProjects,
        long totalLinkedCommits,
        long tasksWithCommitCount,
        long tasksMissingCommitCount,
        Map<String, Long> commitsPerAuthor,
        Map<String, Long> tasksPerAssignee,
        String generatedAt
) {
}
