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
        String generatedAt,
        String profileName
) {
}
