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
