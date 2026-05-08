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

import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.usecase.port.out.ProjectManagerResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class JiraAdapterTest {

    static WireMockServer wireMock;
    JiraAdapter adapter;

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
        adapter = new JiraAdapter();
    }

    private Profile profile(String token, Set<Long> issueTypeIds) {
        return new Profile(
                "test-profile",
                ProjectManagerType.JIRA,
                SourceControlType.GITHUB,
                wireMock.baseUrl(),
                token,
                issueTypeIds,
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

    /** Profile with a raw token (will be Base64-encoded by the adapter). */
    private Profile defaultProfile() {
        return profile("user:token", Set.of());
    }

    private String versionJson(String versionId, String name, String projectId,
                               boolean released, String releaseDate) {
        return """
                {
                  "id": "%s",
                  "name": "%s",
                  "projectId": "%s",
                  "released": %b,
                  "releaseDate": "%s"
                }
                """.formatted(versionId, name, projectId, released, releaseDate);
    }

    private String projectJson(String projectId, String projectKey) {
        return """
                {
                  "id": "%s",
                  "key": "%s",
                  "name": "Test Project"
                }
                """.formatted(projectId, projectKey);
    }

    private String issueTypesJson(long... ids) {
        StringBuilder types = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) types.append(",");
            types.append("""
                    {"id": %d, "name": "Type-%d"}
                    """.formatted(ids[i], ids[i]));
        }
        types.append("]");
        return types.toString();
    }

    private String searchResultJson(int total, String... keys) {
        StringBuilder issues = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) issues.append(",");
            issues.append(issueJson(keys[i], "Summary for " + keys[i], "Dev " + i, "In Progress"));
        }
        return """
                {
                  "total": %d,
                  "issues": [ %s ]
                }
                """.formatted(total, issues);
    }

    private String emptySearchResult() {
        return """
                {
                  "total": 0,
                  "issues": []
                }
                """;
    }

    private String issueJson(String key, String summary, String assignee, String status) {
        return """
                {
                  "key": "%s",
                  "fields": {
                    "summary": "%s",
                    "assignee": { "displayName": "%s" },
                    "status":   { "name": "%s" }
                  }
                }
                """.formatted(key, summary, assignee, status);
    }

    /** GET /rest/api/3/version/{versionId} */
    private void stubVersion(String versionId, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/version/" + versionId))
                .willReturn(okJson(body)));
    }

    /** GET /rest/api/3/project/{projectId} */
    private void stubProject(String projectId, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/project/" + projectId))
                .willReturn(okJson(body)));
    }

    /** GET /rest/api/3/issuetype */
    private void stubIssueTypes(String body) {
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/issuetype"))
                .willReturn(okJson(body)));
    }

    /** POST /rest/api/3/search/jql */
    private void stubSearch(int startAt, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("startAt", equalTo(String.valueOf(startAt)))
                .willReturn(okJson(body)));
    }

    private void stubHappyPath(String versionId, String... issueKeys) {
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, "2024-06-01"));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, searchResultJson(issueKeys.length, issueKeys));
    }

    @Test
    @DisplayName("Should populate ProjectManagerInfo with correct version metadata")
    void populatesVersionMetadata() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v2.3", "20001", true, "2024-03-15"));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().versionName(), is("v2.3"));
        assertThat(result.pmInfo().versionId(), is(versionId));
        assertThat(result.pmInfo().versionStatus(), is("Released"));
        assertThat(result.pmInfo().dueDate(), is("2024-03-15"));
    }

    @Test
    @DisplayName("Should mark version as Unreleased when released flag is false")
    void marksVersionAsUnreleased() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v3.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().versionStatus(), is("Unreleased"));
    }

    @Test
    @DisplayName("Should build the correct version URL from baseUrl, projectId and versionId")
    void buildsCorrectVersionUrl() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        String expectedUrl = wireMock.baseUrl() + "/projects/20001/versions/" + versionId;
        assertThat(result.pmInfo().versionUrl(), is(expectedUrl));
    }

    @Test
    @DisplayName("Should fall back to projectId as project key when project endpoint fails")
    void fallsBackToProjectIdWhenProjectEndpointFails() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/project/20001"))
                .willReturn(serverError()));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().projectId(), is("20001"));
    }

    @Test
    @DisplayName("Should encode a raw token as Basic Auth header")
    void encodesRawTokenAsBasicAuth() {
        String versionId = "10001";
        String rawToken = "user@example.com:mypassword";
        String expectedHeader = "Basic " + Base64.getEncoder().encodeToString(rawToken.getBytes());

        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/version/" + versionId))
                .withHeader("Authorization", equalTo(expectedHeader))
                .willReturn(okJson(versionJson(versionId, "v1.0", "20001", false, ""))));

        adapter.fetchTasksByVersion(profile(rawToken, Set.of()), versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/version/" + versionId))
                .withHeader("Authorization", equalTo(expectedHeader)));
    }

    @Test
    @DisplayName("Should pass through a token that already starts with 'Bearer'")
    void passesThroughBearerToken() {
        String versionId = "10001";
        String bearerToken = "Bearer my-jwt-token";

        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        adapter.fetchTasksByVersion(profile(bearerToken, Set.of()), versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/version/" + versionId))
                .withHeader("Authorization", equalTo(bearerToken)));
    }

    @Test
    @DisplayName("Should pass through a token that already starts with 'Basic'")
    void passesThroughBasicToken() {
        String versionId = "10001";
        String basicToken = "Basic already-encoded==";

        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        adapter.fetchTasksByVersion(profile(basicToken, Set.of()), versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/version/" + versionId))
                .withHeader("Authorization", equalTo(basicToken)));
    }

    @Test
    @DisplayName("Should use wildcard tracker when no issue type IDs are configured")
    void usesWildcardTrackerWhenNoIssueTypeConfigured() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson(1L, 2L, 3L));
        stubSearch(0, emptySearchResult());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().trackers(), hasSize(1));
        assertThat(result.pmInfo().trackers().get(0).id(), is("*"));
    }

    @Test
    @DisplayName("Should filter trackers by the configured issue type IDs")
    void filtersTrackersByConfiguredIds() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson(10L, 20L, 30L));
        stubSearch(0, emptySearchResult());

        Profile p = profile("user:token", Set.of(10L, 30L));
        ProjectManagerResult result = adapter.fetchTasksByVersion(p, versionId);

        assertThat(result.pmInfo().trackers(), hasSize(2));
        assertThat(result.pmInfo().trackers().stream().map(t -> t.id()).toList(),
                containsInAnyOrder("10", "30"));
    }

    @Test
    @DisplayName("Should not include issue types that are not in the configured ID set")
    void doesNotIncludeUnconfiguredIssueTypes() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson(10L, 20L, 30L));
        stubSearch(0, emptySearchResult());

        Profile p = profile("user:token", Set.of(10L));
        ProjectManagerResult result = adapter.fetchTasksByVersion(p, versionId);

        assertThat(result.pmInfo().trackers().stream().map(t -> t.id()).toList(),
                not(hasItems("20", "30")));
    }

    @Test
    @DisplayName("Should query all issue types when wildcard tracker is used")
    void queryWithoutIssueTypeFilterWhenWildcard() {
        String versionId = "10001";
        stubHappyPath(versionId);

        adapter.fetchTasksByVersion(defaultProfile(), versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("jql", not(containing("issuetype"))));
    }

    @Test
    @DisplayName("Should include issuetype filter in JQL when specific trackers are configured")
    void queryIncludesIssueTypeFilterWhenTrackersConfigured() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson(10L, 20L));
        stubSearch(0, emptySearchResult());

        Profile p = profile("user:token", Set.of(10L, 20L));
        adapter.fetchTasksByVersion(p, versionId);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("jql", containing("issuetype in")));
    }

    @Test
    @DisplayName("Should return all tasks found for the given version")
    void returnsAllTasksForVersion() {
        String versionId = "10001";
        stubHappyPath(versionId, "PROJ-1", "PROJ-2", "PROJ-3");

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), hasSize(3));
    }

    @Test
    @DisplayName("Should fetch all pages when result exceeds a single page")
    void fetchesAllPagesWhenResultExceedsOnePage() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());

        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson(searchResultJson(3, "PROJ-1", "PROJ-2"))));

        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("startAt", equalTo("2"))
                .willReturn(okJson(searchResultJson(3, "PROJ-3"))));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), hasSize(3));
        assertThat(result.tasks().stream().map(t -> t.id()).toList(),
                containsInAnyOrder("PROJ-1", "PROJ-2", "PROJ-3"));
    }

    @Test
    @DisplayName("Should return empty task list when version has no issues")
    void returnsEmptyTaskListWhenNoIssuesFound() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        stubSearch(0, emptySearchResult());

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), empty());
    }

    @Test
    @DisplayName("Should map task key, summary, assignee and status correctly")
    void mapsTaskFieldsCorrectly() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson("""
                        {
                          "total": 1,
                          "issues": [
                            {
                              "key": "PROJ-99",
                              "fields": {
                                "summary": "Fix critical bug",
                                "assignee": { "displayName": "Alice" },
                                "status":   { "name": "Done" }
                              }
                            }
                          ]
                        }
                        """)));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks(), hasSize(1));
        var task = result.tasks().get(0);
        assertThat(task.id(), is("PROJ-99"));
        assertThat(task.title(), is("Fix critical bug"));
        assertThat(task.assignee(), is("Alice"));
        assertThat(task.status(), is("Done"));
    }

    @Test
    @DisplayName("Should build the correct browse URL for each task")
    void buildsCorrectTaskUrl() {
        String versionId = "10001";
        stubHappyPath(versionId, "PROJ-7");

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        String expectedUrl = wireMock.baseUrl() + "/browse/PROJ-7";
        assertThat(result.tasks().get(0).url(), is(expectedUrl));
    }

    @Test
    @DisplayName("Should use 'Unassigned' when assignee field is absent")
    void usesUnassignedWhenAssigneeIsAbsent() {
        String versionId = "10001";
        stubVersion(versionId, versionJson(versionId, "v1.0", "20001", false, ""));
        stubProject("20001", projectJson("20001", "PROJ"));
        stubIssueTypes(issueTypesJson());
        wireMock.stubFor(get(urlPathEqualTo("/rest/api/3/search/jql"))
                .withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson("""
                        {
                          "total": 1,
                          "issues": [
                            {
                              "key": "PROJ-1",
                              "fields": {
                                "summary": "Orphan task",
                                "assignee": null,
                                "status": { "name": "Open" }
                              }
                            }
                          ]
                        }
                        """)));

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.tasks().get(0).assignee(), is("Unassigned"));
    }

    @Test
    @DisplayName("Should populate ProjectManagerInfo with correct provider name and base URL")
    void populatesProviderInfo() {
        String versionId = "10001";
        stubHappyPath(versionId);

        ProjectManagerResult result = adapter.fetchTasksByVersion(defaultProfile(), versionId);

        assertThat(result.pmInfo().providerName(), is("Jira"));
        assertThat(result.pmInfo().providerUrl(), is(wireMock.baseUrl()));
    }
}