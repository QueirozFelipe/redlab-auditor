package com.redlab.auditor.domain.model;

import java.util.List;

public record AuditReportItem(
        Task task,
        List<Commit> foundCommits,
        AuditStatus status,
        String observation
) {
}
