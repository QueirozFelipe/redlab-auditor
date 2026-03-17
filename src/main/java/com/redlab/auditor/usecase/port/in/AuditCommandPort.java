package com.redlab.auditor.usecase.port.in;

import com.redlab.auditor.domain.model.AuditReport;

import java.util.List;

/**
 * Primary entry point for the audit logic.
 */
public interface AuditCommandPort {
    /**
     * Executes the complete audit process, cross-referencing tasks and commits,
     * and triggering the report generation.
     *
     * @param version        The target version/release identifier from the project manager.
     * @param sourceBranches A prioritized list of branches containing new development (e.g., ["dev", "develop"]).
     * @param targetBranches A prioritized list of stable/production branches (e.g., ["main", "master"]).
     * @return An {@link AuditReport} object containing the consolidated audit results.
     */
    AuditReport execute(String version, String profileName, List<String> sourceBranches, List<String> targetBranches);
}