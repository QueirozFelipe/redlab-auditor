package com.redlab.auditor.adapter.out.template;

import com.redlab.auditor.domain.model.AuditReport;
import com.redlab.auditor.infrastructure.util.StorageUtils;
import com.redlab.auditor.usecase.port.out.ReportGeneratorPort;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@ApplicationScoped
public class QuteReportAdapter implements ReportGeneratorPort {

    private final Template report;

    @Inject
    public QuteReportAdapter(Template report) {
        this.report = report;
    }

    @Override
    public void generateHtmlReport(AuditReport auditReport) {
        String logoBase64 = getLogoBase64();

        TemplateInstance instance = report
                .data("reportData", auditReport)
                .data("logoBase64", logoBase64);

        String htmlContent = instance.render();

        try {
            String fileName = "redlab-audit-v" + auditReport.targetVersion() + ".html";
            Path outputPath = StorageUtils.getReportsPath().resolve(fileName);
            Files.writeString(outputPath, htmlContent);

            System.out.println("[\u2713] Audit Report successfully generated at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error writing the HTML report to disk: " + e.getMessage(), e);
        }
    }

    private String getLogoBase64() {
        try {
            Path path = Path.of("src/main/resources/META-INF/resources/redlab-logo.png");
            byte[] imageBytes = Files.readAllBytes(path);
            String base64Content = Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/png;base64," + base64Content;
        } catch (Exception e) {
            System.err.println("[\u26A0] Warning: Could not load logo for Base64 injection: " + e.getMessage());
            return "";
        }
    }

}
