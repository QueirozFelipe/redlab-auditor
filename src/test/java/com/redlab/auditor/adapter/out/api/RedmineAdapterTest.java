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

package com.redlab.auditor.adapter.out.api;

import com.redlab.auditor.domain.exception.ResourceNotFoundException;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.usecase.port.out.ProjectManagerResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class RedmineAdapterTest {

    static WireMockServer wireMock;
    RedmineAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        adapter = new RedmineAdapter();
    }

    private Profile profile(String token, Set<Long> trackerIds) {
        return new Profile(
                "test-profile",
                ProjectManagerType.REDMINE,
                SourceControlType.GITHUB,
                wireMock.baseUrl(),
                token,
                trackerIds,
                "http://sc.local",
                "sc-token",
                "group-id",
                10,
                Set.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private Profile defaultProfile() {
        return profile("my-api-token", Set.of());
    }

    private String versionJson(String versionId, String versionName,
                               String projectId, String projectName,
                               String status, String dueDate) {
        return """
                {
                  "version": {
                    "id": %s,
                    "name": "%s",
                    "status": "%s",
                    "due_date": "%s",
                    "project": {
                      "id": %s,
                      "name": "%s"
                    }
                  }
                }
                """.formatted(versionId, versionName, status, dueDate, projectId, projectName);
    }

    private String trackersJson(long... ids) {
        StringBuilder trackers = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) trackers.append(",");
            trackers.append("""
                    {"id": %d, "name": "Tracker-%d"}
                    """.formatted(ids[i], ids[i]));
        }
        trackers.append("]");
        return "{ \"trackers\": %s }".formatted(trackers);
    }

    private String issuesJson(int totalCount, String... ids) {
        StringBuilder issues = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) issues.append(",");
            issues.append(issueJson(ids[i], "Subject for " + ids[i], "Dev " + i, "New"));
        }
        return """
                {
                  "total_count": %d,
                  "issues": [ %s ]
                }
                """.formatted(totalCount, issues);
    }

    private String emptyIssuesJson() {
        return """
                {
                  "total_count": 0,
                  "issues": []
                }
                """;
    }

    private String issueJson(String id, String subject, String assignedTo, String status) {
        return """
                {
                  "id": %s,
                  "subject": "%s",
                  "assigned_to": { "name": "%s" },
                  "status": { "name": "%s" }
                }
                """.formatted(id, subject, assignedTo, status);
    }

    /** GET /versions/{versionId}.json */
    private void stubVersion(String versionId, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/versions/" + versionId + ".json"))
                .willReturn(okJson(body)));
    }

    /** GET /trackers.json */
    private void stubTrackers(String body) {
        wireMock.stubFor(get(urlPathEqualTo("/trackers.json"))
                .willReturn(okJson(body)));
    }

    /** GET /issues.json — stubs a specific offset page. */
    private void stubIssues(int offset, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/issues.json"))
                .withQueryParam("offset", equalTo(String.valueOf(offset)))
                .willReturn(okJson(body)));
    }

    private void stubHappyPath(String versionId, String... issueIds) {
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "My Project", "open", "2024-06-01"));
        stubTrackers(trackersJson());
        stubIssues(0, issuesJson(issueIds.length, issueIds));
    }

    @Test
    @DisplayName("Should populate ProjectManagerInfo with correct version metadata")
    void populatesVersionMetadata() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "Sprint 3", "10", "Backend", "open", "2024-09-30"));
        stubTrackers(trackersJson());
        stubIssues(0, emptyIssuesJson());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().versionName(), is("Sprint 3"));
        assertThat(result.pmInfo().versionId(), is(versionId));
        assertThat(result.pmInfo().versionStatus(), is("open"));
        assertThat(result.pmInfo().dueDate(), is("2024-09-30"));
    }

    @Test
    @DisplayName("Should resolve project name and ID from nested version response")
    void resolvesProjectInfoFromVersionNode() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v2.0", "42", "Core API", "open", ""));
        stubTrackers(trackersJson());
        stubIssues(0, emptyIssuesJson());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().projectName(), is("Core API"));
        assertThat(result.pmInfo().projectId(), is("42"));
    }

    @Test
    @DisplayName("Should build the correct version URL from baseUrl and versionId")
    void buildsCorrectVersionUrl() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson());
        stubIssues(0, emptyIssuesJson());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        String expectedUrl = wireMock.baseUrl() + "/versions/" + versionId;
        assertThat(result.pmInfo().versionUrl(), is(expectedUrl));
    }

    @Test
    @DisplayName("Should use wildcard tracker when no tracker IDs are configured")
    void usesWildcardTrackerWhenNoTrackerConfigured() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson(1L, 2L, 3L));
        stubIssues(0, emptyIssuesJson());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().trackers(), hasSize(1));
        assertThat(result.pmInfo().trackers().get(0).id(), is("*"));
    }

    @Test
    @DisplayName("Should filter trackers by the configured IDs")
    void filtersTrackersByConfiguredIds() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson(10L, 20L, 30L));
        stubIssues(0, emptyIssuesJson());

        Profile p = profile("my-api-token", Set.of(10L, 30L));
        ProjectManagerResult result = adapter.fetchTasksByVersion(p, versionId);

        assertThat(result.pmInfo().trackers(), hasSize(2));
        assertThat(result.pmInfo().trackers().stream().map(t -> t.id()).toList(),
                containsInAnyOrder("10", "30"));
    }

    @Test
    @DisplayName("Should not include trackers whose ID is not in the configured set")
    void doesNotIncludeUnconfiguredTrackers() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson(10L, 20L, 30L));
        stubIssues(0, emptyIssuesJson());

        Profile p = profile("my-api-token", Set.of(10L));
        ProjectManagerResult result = adapter.fetchTasksByVersion(p, versionId);

        assertThat(result.pmInfo().trackers().stream().map(t -> t.id()).toList(),
                not(hasItems("20", "30")));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when configured tracker IDs do not exist in Redmine")
    void throwsWhenConfiguredTrackerIdsNotFoundInRedmine() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson(99L));

        Profile p = profile("my-api-token", Set.of(1L, 2L));

        assertThrows(ResourceNotFoundException.class,
                () -> adapter.fetchTasksByVersion(p, versionId));
    }

    @Test
    @DisplayName("Should return all tasks found for the given version")
    void returnsAllTasksForVersion() {
        String versionId = "5";
        stubHappyPath(versionId, "10", "11", "12");

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), hasSize(3));
    }

    @Test
    @DisplayName("Should fetch all pages when result exceeds a single page")
    void fetchesAllPagesWhenResultExceedsOnePage() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson());

        wireMock.stubFor(get(urlPathEqualTo("/issues.json"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson(issuesJson(3, "1", "2"))));

        wireMock.stubFor(get(urlPathEqualTo("/issues.json"))
                .withQueryParam("offset", equalTo("2"))
                .willReturn(okJson(issuesJson(3, "3"))));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), hasSize(3));
        assertThat(result.tasks().stream().map(t -> t.id()).toList(),
                containsInAnyOrder("1", "2", "3"));
    }

    @Test
    @DisplayName("Should return empty task list when version has no issues")
    void returnsEmptyTaskListWhenNoIssuesFound() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson());
        stubIssues(0, emptyIssuesJson());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), empty());
    }

    @Test
    @DisplayName("Should send tracker IDs as a comma-separated query param when trackers are configured")
    void sendsTrackerParamWhenTrackersConfigured() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson(10L, 20L));
        stubIssues(0, emptyIssuesJson());

        Profile p = profile("my-api-token", Set.of(10L, 20L));
        adapter.fetchTasksByVersion(p, versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/issues.json"))
                .withQueryParam("tracker_id", matching("10,20|20,10")));
    }

    @Test
    @DisplayName("Should not send tracker_id param when wildcard tracker is used")
    void doesNotSendTrackerParamWhenWildcard() {
        String versionId = "5";
        stubHappyPath(versionId);

        adapter.fetchTasksByVersion(defaultProfile(), versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/issues.json"))
                .withQueryParam("tracker_id", equalTo("")));
    }

    @Test
    @DisplayName("Should map task id, subject, assigned_to and status correctly")
    void mapsTaskFieldsCorrectly() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson());
        wireMock.stubFor(get(urlPathEqualTo("/issues.json"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {
                          "total_count": 1,
                          "issues": [
                            {
                              "id": 42,
                              "subject": "Fix critical bug",
                              "assigned_to": { "name": "Alice" },
                              "status": { "name": "In Progress" }
                            }
                          ]
                        }
                        """)));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), hasSize(1));
        var task = result.tasks().get(0);
        assertThat(task.id(), is("42"));
        assertThat(task.title(), is("Fix critical bug"));
        assertThat(task.assignee(), is("Alice"));
        assertThat(task.status(), is("In Progress"));
    }

    @Test
    @DisplayName("Should build the correct issue URL from baseUrl and issue id")
    void buildsCorrectTaskUrl() {
        String versionId = "5";
        stubHappyPath(versionId, "99");

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        String expectedUrl = wireMock.baseUrl() + "/issues/99";
        assertThat(result.tasks().get(0).url(), is(expectedUrl));
    }

    @Test
    @DisplayName("Should use 'Unassigned' when assigned_to field is absent")
    void usesUnassignedWhenAssignedToIsAbsent() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson());
        wireMock.stubFor(get(urlPathEqualTo("/issues.json"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {
                          "total_count": 1,
                          "issues": [
                            {
                              "id": 7,
                              "subject": "Orphan issue",
                              "status": { "name": "New" }
                            }
                          ]
                        }
                        """)));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks().get(0).assignee(), is("Unassigned"));
    }

    @Test
    @DisplayName("Should use 'No subject' when subject field is absent")
    void usesNoSubjectWhenSubjectIsAbsent() {
        String versionId = "5";
        stubVersion(versionId, versionJson(versionId, "v1.0", "1", "Project", "open", ""));
        stubTrackers(trackersJson());
        wireMock.stubFor(get(urlPathEqualTo("/issues.json"))
                .withQueryParam("offset", equalTo("0"))
                .willReturn(okJson("""
                        {
                          "total_count": 1,
                          "issues": [
                            {
                              "id": 8,
                              "assigned_to": { "name": "Bob" },
                              "status": { "name": "New" }
                            }
                          ]
                        }
                        """)));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks().get(0).title(), is("No subject"));
    }

    @Test
    @DisplayName("Should populate ProjectManagerInfo with correct provider name and base URL")
    void populatesProviderInfo() {
        String versionId = "5";
        stubHappyPath(versionId);

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().providerName(), is("Redmine"));
        assertThat(result.pmInfo().providerUrl(), is(wireMock.baseUrl()));
    }
}