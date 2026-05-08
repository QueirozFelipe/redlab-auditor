/*
 * Copyright 2026 Felipe Queiroz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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