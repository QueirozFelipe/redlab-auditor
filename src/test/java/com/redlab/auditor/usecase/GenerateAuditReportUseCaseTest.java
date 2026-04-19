package com.redlab.auditor.usecase;

import com.redlab.auditor.domain.model.*;
import com.redlab.auditor.infrastructure.security.ProfileStorageService;
import com.redlab.auditor.usecase.factory.ProjectManagerFactory;
import com.redlab.auditor.usecase.factory.SourceControlFactory;
import com.redlab.auditor.usecase.port.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateAuditReportUseCaseTest {

    @Mock
    SourceControlFactory scFactory;
    @Mock
    ProjectManagerFactory pmFactory;
    @Mock
    ReportGeneratorPort reportGeneratorPort;
    @Mock
    ProfileStorageService profileStorageService;

    @Mock
    SourceControlPort scPort;
    @Mock
    ProjectManagerPort pmPort;

    @InjectMocks
    GenerateAuditReportUseCase useCase;

    private Profile testProfile;

    @BeforeEach
    void setUp() {
        testProfile = new Profile(
                "test-profile",
                ProjectManagerType.REDMINE,
                SourceControlType.GITLAB,
                "http://redmine",
                "token",
                Set.of(1L),
                "http://gitlab",
                "token",
                "group-1",
                10,
                Set.of(1L),
                List.of("dev"),
                List.of("main"),
                "TASK-\\d+"
        );

        useCase.toolVersion = "1.0.0-test";
    }

    @Test
    void shouldGenerateReportWithSuccess() {
        String profileName = "default";
        String version = "v1";
        List<String> branches = List.of("main");

        when(profileStorageService.loadProfiles()).thenReturn(Map.of(profileName, testProfile));
        when(scFactory.getAdapter(any())).thenReturn(scPort);
        when(pmFactory.getAdapter(any())).thenReturn(pmPort);

        Task task = new Task("TASK-123", "Fix bug", "Felipe", "In Progress", "http://url");
        ProjectManagerInfo pmInfo = mock(ProjectManagerInfo.class);
        when(pmPort.fetchTasksByVersion(any(), any())).thenReturn(new ProjectManagerResult(pmInfo, List.of(task)));

        Commit linkedCommit = new Commit("h1", "TASK-123 working on it", "Felipe", "p1", List.of("TASK-123"), "url", "2026-04-18T00:00:00Z");
        Commit orphanCommit = new Commit("h2", "random fix", "Gabriel", "p1", List.of(), "url", "2026-04-18T01:00:00Z");

        SourceControlInfo scInfo = mock(SourceControlInfo.class);
        when(scPort.compareBranches(any(), any(), any())).thenReturn(new SourceControlResult(
                scInfo, List.of(linkedCommit, orphanCommit), List.of(), List.of(), List.of()
        ));

        AuditReport report = useCase.execute(version, profileName, branches, branches);

        assertNotNull(report);
        assertEquals("1.0.0-test", report.toolVersion());
        assertEquals(2, report.totalCommitsCount());
        assertEquals(1, report.totalLinkedCommits());
        assertEquals(1, report.orphanCommits().size());
        assertEquals(1, report.tasksWithCommitCount());

        assertEquals(AuditStatus.OK, report.items().get(0).status());

        verify(reportGeneratorPort, times(1)).generateHtmlReport(any());
    }

    @Test
    void shouldThrowExceptionWhenProfileNotFound() {
        when(profileStorageService.loadProfiles()).thenReturn(Collections.emptyMap());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                useCase.execute("v1", "invalid", List.of("main"), List.of("main"))
        );

        assertTrue(exception.getMessage().contains("Profile not found"));
    }

    @Test
    void shouldReturnWarningWhenTaskHasNoCommits() {
        when(profileStorageService.loadProfiles()).thenReturn(Map.of("p", testProfile));
        when(scFactory.getAdapter(any())).thenReturn(scPort);
        when(pmFactory.getAdapter(any())).thenReturn(pmPort);

        Task task = new Task("TASK-999", "No commit task", "Felipe", "Closed", "url");
        when(pmPort.fetchTasksByVersion(any(), any())).thenReturn(new ProjectManagerResult(mock(ProjectManagerInfo.class), List.of(task)));
        when(scPort.compareBranches(any(), any(), any())).thenReturn(new SourceControlResult(
                mock(SourceControlInfo.class), List.of(), List.of(), List.of(), List.of()
        ));

        AuditReport report = useCase.execute("v1", "p", List.of("m"), List.of("m"));

        assertEquals(AuditStatus.WARNING, report.items().get(0).status());
        assertEquals(1, report.tasksMissingCommitCount());
    }
}