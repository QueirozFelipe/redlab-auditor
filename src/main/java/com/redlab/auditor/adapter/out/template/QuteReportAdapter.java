package com.redlab.auditor.adapter.out.template;

import com.redlab.auditor.domain.model.AuditReport;
import com.redlab.auditor.usecase.port.out.ReportGeneratorPort;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ApplicationScoped
public class QuteReportAdapter implements ReportGeneratorPort {

    private final Template report;

    @Inject
    public QuteReportAdapter(Template report) {
        this.report = report;
    }

    @Override
    public void generateHtmlReport(AuditReport auditReport) {
        TemplateInstance instance = report.data("reportData", auditReport);

        String htmlContent = instance.render();

        try {
            String fileName = "redlab-audit-v" + auditReport.targetVersion() + ".html";
            Path outputPath = Paths.get(fileName);
            Files.writeString(outputPath, htmlContent);

            System.out.println("[\u2713] Audit Report successfully generated at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error writing the HTML report to disk: " + e.getMessage(), e);
        }
    }

}
