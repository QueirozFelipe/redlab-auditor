package com.redlab.auditor.usecase;

import com.redlab.auditor.domain.model.*;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import com.redlab.auditor.usecase.factory.ProjectManagerFactory;
import com.redlab.auditor.usecase.factory.SourceControlFactory;
import com.redlab.auditor.usecase.port.in.AuditCommandPort;
import com.redlab.auditor.usecase.port.out.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class GenerateAuditReportUseCase implements AuditCommandPort {

    private final ProjectManagerFactory projectManagerFactory;
    private final SourceControlFactory sourceControlFactory;
    private final ReportGeneratorPort reportGeneratorPort;
    private final ProfileStorageService profileStorageService;
    @ConfigProperty(name = "quarkus.application.version")
    String toolVersion;

    @Inject
    public GenerateAuditReportUseCase(
            SourceControlFactory scFactory,
            ProjectManagerFactory pmFactory,
            ReportGeneratorPort reportGeneratorPort,
            ProfileStorageService profileStorageService) {
        this.sourceControlFactory = scFactory;
        this.projectManagerFactory = pmFactory;
        this.reportGeneratorPort = reportGeneratorPort;
        this.profileStorageService = profileStorageService;
    }

    @Override
    public AuditReport execute(String version, String profileName, List<String> sourceBranches, List<String> targetBranches) {
        Profile profile = profileStorageService.loadProfiles().get(profileName);
        if (profile == null) throw new RuntimeException("Profile not found: " + profileName);

        SourceControlPort scPort = sourceControlFactory.getAdapter(profile.scType());
        ProjectManagerPort pmPort = projectManagerFactory.getAdapter(profile.pmType());

        ProjectManagerResult pmResult = pmPort.fetchTasksByVersion(profile, version);
        ProjectManagerInfo pmInfo = pmResult.pmInfo();
        List<Task> tasks = pmResult.tasks();

        Set<String> validTaskIds = tasks.stream().map(Task::id).collect(Collectors.toSet());

        Map<String, Long> tasksPerAssignee = tasks.stream()
                .collect(Collectors.groupingBy(Task::assignee, Collectors.counting()));

        SourceControlResult scResult = scPort.compareBranches(profile, sourceBranches, targetBranches);
        SourceControlInfo scInfo = scResult.scInfo();
        List<Commit> commits = scResult.commits();

        Map<String, Long> commitsPerAuthor = commits.stream()
                .collect(Collectors.groupingBy(Commit::author, Collectors.counting()));

        List<AuditReportItem> reportItems = tasks.stream().map(task -> {
            List<Commit> relatedCommits = commits.stream()
                    .filter(commit -> commit.associatedTaskIds().contains(task.id()))
                    .toList();

            AuditStatus status = determineStatus(task, relatedCommits);

            return new AuditReportItem(task, relatedCommits, status);
        }).toList();

        List<Commit> orphanCommits = commits.stream()
                .filter(commit -> commit.associatedTaskIds().stream().noneMatch(validTaskIds::contains))
                .toList();

        long tasksWithCommitCount = reportItems.stream().filter(item -> !item.foundCommits().isEmpty()).count();
        long tasksMissingCommitCount = reportItems.size() - tasksWithCommitCount;
        long totalCommitsCount = commits.size();
        long totalLinkedCommits = totalCommitsCount - orphanCommits.size();

        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        AuditReport report = new AuditReport(
                toolVersion,
                version,
                pmInfo,
                scInfo,
                reportItems,
                totalCommitsCount,
                orphanCommits,
                scResult.activeProjects(),
                scResult.missingBranchProjects(),
                scResult.ignoredByUserProjects(),
                totalLinkedCommits,
                tasksWithCommitCount,
                tasksMissingCommitCount,
                commitsPerAuthor,
                tasksPerAssignee,
                generatedAt,
                profileName
        );

        reportGeneratorPort.generateHtmlReport(report);

        return report;
    }

    private AuditStatus determineStatus(Task task, List<Commit> relatedCommits) {
        if (relatedCommits.isEmpty()) return AuditStatus.WARNING;
        return AuditStatus.OK;
    }

}