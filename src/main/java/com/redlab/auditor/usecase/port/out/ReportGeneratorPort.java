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

package com.redlab.auditor.usecase.port.out;

import com.redlab.auditor.domain.model.AuditReport;

public interface ReportGeneratorPort {
    /**
     * Processes the consolidated audit report and generates a self-contained HTML page.
     *
     * @param report The domain object containing all validated and cross-referenced data.
     */
    void generateHtmlReport(AuditReport report);
}
