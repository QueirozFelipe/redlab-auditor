package com.redlab.auditor.usecase;

import com.redlab.auditor.domain.model.*;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.redlab.auditor.usecase.port.out.ReportGeneratorPort;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class GenerateAuditReportUseCase {

    private final ProjectManagerPort projectManagerPort;
    private final SourceControlPort sourceControlPort;
    private final ReportGeneratorPort reportGeneratorPort;

    @Inject
    public GenerateAuditReportUseCase(
            ProjectManagerPort projectManagerPort,
            SourceControlPort sourceControlPort,
            ReportGeneratorPort reportGeneratorPort) {
        this.projectManagerPort = projectManagerPort;
        this.sourceControlPort = sourceControlPort;
        this.reportGeneratorPort = reportGeneratorPort;
    }

    public AuditReport execute(String version, String productionBranch, String targetBranch) {
        List<Task> tasks = projectManagerPort.fetchTasksByVersion(version);
        Set<String> validTaskIds = tasks.stream()
                .map(Task::id)
                .collect(Collectors.toSet());

        List<Commit> commits = sourceControlPort.fetchCommitsSinceLastTag(productionBranch, targetBranch);

        List<AuditReportItem> reportItems = tasks.stream().map(task -> {
            List<Commit> relatedCommits = commits.stream()
                    .filter(commit -> commit.associatedTaskIds().contains(task.id()))
                    .toList();

            AuditStatus status = determineStatus(task, relatedCommits);
            String observation = generateObservation(status, relatedCommits);

            return new AuditReportItem(task, relatedCommits, status, observation);
        }).toList();

        List<Commit> orphanCommits = commits.stream()
                .filter(commit -> commit.associatedTaskIds().stream().noneMatch(validTaskIds::contains))
                .toList();

        AuditReport report = new AuditReport(version, reportItems, orphanCommits);

        reportGeneratorPort.generateHtmlReport(report);

        return report;
    }

    private AuditStatus determineStatus(Task task, List<Commit> relatedCommits) {
        if (relatedCommits.isEmpty()) {
            return AuditStatus.PROBLEM;
        }

        // Espaço reservado para lógicas futuras de WARNING.
        // Exemplo: if (!commit.author().equals(task.assignee())) return AuditStatus.WARNING;

        return AuditStatus.OK;
    }

    private String generateObservation(AuditStatus status, List<Commit> relatedCommits) {
        return switch (status) {
            case PROBLEM -> "No commits found for this task in the target branch.";
            case WARNING -> "Authorship divergence detected.";
            case OK -> relatedCommits.size() + " commit(s) validated.";
        };
    }
}
