package com.redlab.auditor.usecase;

import com.redlab.auditor.domain.model.*;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import com.redlab.auditor.usecase.port.in.AuditCommandPort;
import com.redlab.auditor.usecase.port.out.ProjectManagerPort;
import com.redlab.auditor.usecase.port.out.ReportGeneratorPort;
import com.redlab.auditor.usecase.port.out.SourceControlPort;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class GenerateAuditReportUseCase implements AuditCommandPort {

    private final ProjectManagerPort projectManagerPort;
    private final SourceControlPort sourceControlPort;
    private final ReportGeneratorPort reportGeneratorPort;
    private final ProfileStorageService profileStorageService;

    @Inject
    public GenerateAuditReportUseCase(
            ProjectManagerPort projectManagerPort,
            SourceControlPort sourceControlPort,
            ReportGeneratorPort reportGeneratorPort,
            ProfileStorageService profileStorageService) {
        this.projectManagerPort = projectManagerPort;
        this.sourceControlPort = sourceControlPort;
        this.reportGeneratorPort = reportGeneratorPort;
        this.profileStorageService = profileStorageService;
    }

    @Override
    public AuditReport execute(String version, String profileName, String targetBranch) {
        Profile profile = profileStorageService.loadProfiles().get(profileName);
        if (profile == null) throw new RuntimeException("Profile not found: " + profileName);

        List<Task> tasks = projectManagerPort.fetchTasksByVersion(profile, version);
        Set<String> validTaskIds = tasks.stream().map(Task::id).collect(Collectors.toSet());

        Map<String, Long> tasksPerAssignee = tasks.stream()
                .collect(Collectors.groupingBy(Task::assignee, Collectors.counting()));

        SourceControlResult scResult = sourceControlPort.fetchCommitsSinceLastTag(profile, targetBranch);
        List<Commit> commits = scResult.commits();

        Map<String, Long> commitsPerAuthor = commits.stream()
                .collect(Collectors.groupingBy(Commit::author, Collectors.counting()));

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

        long tasksWithCommitCount = reportItems.stream().filter(item -> !item.foundCommits().isEmpty()).count();
        long tasksMissingCommitCount = reportItems.size() - tasksWithCommitCount;
        long totalLinkedCommits = commits.size() - orphanCommits.size();

        AuditReport report = new AuditReport(
                version,
                reportItems,
                orphanCommits,
                scResult.activeProjects(),
                scResult.ignoredProjects(),
                totalLinkedCommits,
                tasksWithCommitCount,
                tasksMissingCommitCount,
                commitsPerAuthor,
                tasksPerAssignee
        );

        reportGeneratorPort.generateHtmlReport(report);

        return report;
    }

    private AuditStatus determineStatus(Task task, List<Commit> relatedCommits) {
        if (relatedCommits.isEmpty()) return AuditStatus.PROBLEM;
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