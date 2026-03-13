package com.redlab.auditor.usecase.port.in;

import com.redlab.auditor.domain.model.AuditReport;

/**
 * Primary entry point for the audit logic.
 */
public interface AuditCommandPort {
    /**
     * Executes the complete audit process, cross-referencing tasks and commits,
     * and triggering the report generation.
     *
     * @param version              The target version/release identifier from the project manager.
     * @param targetBranch The update branch containing the new commits to be audited.
     * @return An {@link AuditReport} object containing the consolidated audit results.
     */
    AuditReport execute(String version, String profileName, String targetBranch);
}