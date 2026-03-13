package com.redlab.auditor.domain.model;

import java.util.List;

public record AuditReport(
        String targetVersion,
        List<AuditReportItem> items,
        List<Commit> orphanCommits
) {
}
