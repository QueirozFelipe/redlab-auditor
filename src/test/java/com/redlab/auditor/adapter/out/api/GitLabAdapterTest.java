package com.redlab.auditor.adapter.out.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.redlab.auditor.domain.model.Profile;
import com.redlab.auditor.domain.model.ProjectManagerType;
import com.redlab.auditor.domain.model.SourceControlType;
import com.redlab.auditor.usecase.port.out.SourceControlResult;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class GitLabAdapterTest {

    static WireMockServer wireMock;
    GitLabAdapter adapter;

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
        adapter = new GitLabAdapter();
    }

    private Profile profile(String groupId,
                            String taskRegex,
                            Set<Long> ignoreIds,
                            int rateLimit,
                            List<String> sourceBranches,
                            List<String> targetBranches) {
        return new Profile(
                "test-profile",
                ProjectManagerType.JIRA,
                SourceControlType.GITLAB,
                "http://pm.local",
                "pm-token",
                Set.of(),
                wireMock.baseUrl(),
                "fake-token",
                groupId,
                rateLimit,
                ignoreIds,
                sourceBranches,
                targetBranches,
                taskRegex
        );
    }

    private Profile defaultProfile(String groupId) {
        return profile(groupId, "([A-Z]+-\\d+)", Set.of(), 10,
                List.of("develop"), List.of("main"));
    }

    private String groupJson(String groupId, String groupName) {
        return """
                {
                  "id": %s,
                  "name": "%s",
                  "full_path": "%s"
                }
                """.formatted(groupId, groupName, groupName.toLowerCase());
    }

    private String projectListJson(long id, String name) {
        return """
                [
                  {
                    "id": %d,
                    "name": "%s",
                    "path_with_namespace": "group/%s"
                  }
                ]
                """.formatted(id, name, name);
    }

    private String emptyList() {
        return "[]";
    }

    private String comparisonJson(String... messages) {
        StringBuilder commits = new StringBuilder();
        for (int i = 0; i < messages.length; i++) {
            if (i > 0) commits.append(",");
            commits.append(commitJson(
                    "abc" + i, messages[i], "2024-01-15T12:00:00.000+00:00",
                    "Dev " + i, "http://gitlab.com/c/" + i));
        }
        return "{ \"commits\": [ %s ] }".formatted(commits);
    }

    private String commitJson(String id, String message, String createdAt, String authorName, String webUrl) {
        return """
                {
                  "id": "%s",
                  "message": "%s",
                  "author_name": "%s",
                  "created_at": "%s",
                  "web_url": "%s"
                }
                """.formatted(id, message, authorName, createdAt, webUrl);
    }

    /** GET /api/v4/groups/{groupId} */
    private void stubGroup(String groupId, String groupName) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v4/groups/" + groupId))
                .willReturn(okJson(groupJson(groupId, groupName))));
    }

    /** GET /api/v4/groups/{groupId}/projects — página 1 retorna projetos, página 2 retorna []. */
    private void stubGroupProjects(String groupId, String projectsJson) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v4/groups/" + groupId + "/projects"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(projectsJson)));

        wireMock.stubFor(get(urlPathEqualTo("/api/v4/groups/" + groupId + "/projects"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(emptyList())));
    }

    /** GET /api/v4/projects/{repoId}/repository/compare */
    private void stubCompare(long repoId, String base, String head, String body) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v4/projects/" + repoId + "/repository/compare"))
                .withQueryParam("from", equalTo(base))
                .withQueryParam("to", equalTo(head))
                .willReturn(okJson(body)));
    }

    private void stubCompareNotFound(long repoId, String base, String head) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v4/projects/" + repoId + "/repository/compare"))
                .withQueryParam("from", equalTo(base))
                .withQueryParam("to", equalTo(head))
                .willReturn(notFound().withBody("{\"message\":\"404 Not Found\"}")));
    }

    @Test
    @DisplayName("Should fetch group name and all projects paginating correctly")
    void fetchesGroupProjectsWithPagination() {
        String groupId = "42";
        stubGroup(groupId, "My Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo-a"));
        stubCompare(1L, "main", "develop", comparisonJson("feat: PROJ-1 add login"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.scInfo().totalProjects(), is(1));
        assertThat(result.scInfo().groupName(), is("My Group"));
    }

    @Test
    @DisplayName("Should automatically append /api/v4 to base URL if not present")
    void appendsApiV4ToBaseUrl() {
        String groupId = "1";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, emptyList());

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.scInfo().providerName(), is("GitLab"));
    }

    @Test
    @DisplayName("Should return empty result when group has no projects")
    void noProjectsReturnsEmptyResult() {
        String groupId = "99";
        stubGroup(groupId, "Empty Group");
        stubGroupProjects(groupId, emptyList());

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), empty());
        assertThat(result.activeProjects(), empty());
        assertThat(result.scInfo().totalProjects(), is(0));
    }

    @Test
    @DisplayName("Should not audit projects whose ID is listed in projectsToIgnore")
    void skipsIgnoredProjects() {
        String groupId = "10";
        long ignoredId = 99L;
        stubGroup(groupId, "Group");

        wireMock.stubFor(get(urlPathEqualTo("/api/v4/groups/" + groupId + "/projects"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("""
                        [
                          {"id": 1,  "name": "repo-ok"},
                          {"id": %d, "name": "repo-ignored"}
                        ]
                        """.formatted(ignoredId))));
        wireMock.stubFor(get(urlPathEqualTo("/api/v4/groups/" + groupId + "/projects"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(emptyList())));

        stubCompare(1L, "main", "develop", comparisonJson("feat: PROJ-1 stuff"));

        Profile p = profile(groupId, "([A-Z]+-\\d+)", Set.of(ignoredId), 10,
                List.of("develop"), List.of("main"));
        SourceControlResult result = adapter.compareBranches(p, List.of("develop"), List.of("main"));

        assertThat(result.ignoredByUserProjects(), hasItem("repo-ignored"));
        assertThat(result.ignoredByUserProjects(), not(hasItem("repo-ok")));
    }

    @Test
    @DisplayName("Should return commits that are ahead of the target branch")
    void returnsAheadCommits() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(5L, "my-repo"));
        stubCompare(5L, "main", "develop",
                comparisonJson("feat: PROJ-42 implement feature", "fix: PROJ-43 fix bug"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(2));
        result.commits().forEach(c -> assertThat(c.projectName(), is("my-repo")));
    }

    @Test
    @DisplayName("Should track projects with no valid branch combination in missingBranchProjects")
    void tracksMissingBranchProjects() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(7L, "no-branch-repo"));
        stubCompareNotFound(7L, "main", "develop");

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.missingBranchProjects(), hasItem("no-branch-repo"));
        assertThat(result.commits(), empty());
    }

    @Test
    @DisplayName("Should try all source×target branch combinations until a valid one is found")
    void triesAllBranchCombinations() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(8L, "multi-branch-repo"));

        stubCompareNotFound(8L, "main", "feature");
        stubCompare(8L, "main", "develop",
                comparisonJson("feat: PROJ-1 works on develop"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("feature", "develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.missingBranchProjects(), empty());
    }

    @Test
    @DisplayName("Should not include merge commits in the result")
    void filtersMergeCommits() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));
        stubCompare(1L, "main", "develop", comparisonJson(
                "Merge branch 'feature' into develop",
                "See merge request group/repo!42",
                "feat: PROJ-1 actual work"
        ));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).message(), is("feat: PROJ-1 actual work"));
    }

    @Test
    @DisplayName("Should not include chore:release commits in the result")
    void filtersReleaseCommits() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));
        stubCompare(1L, "main", "develop", comparisonJson(
                "chore: release 1.2.3",
                "chore:release v2.0",
                "fix: PROJ-5 real fix"
        ));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).message(), containsString("PROJ-5"));
    }

    @Test
    @DisplayName("Should not include 'See merge request' commits — GitLab-specific pattern")
    void filtersSeesMergeRequestCommits() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));
        stubCompare(1L, "main", "develop", comparisonJson(
                "See merge request group/repo!10",
                "feat: PROJ-7 real commit"
        ));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).message(), containsString("PROJ-7"));
    }

    @Test
    @DisplayName("Should extract multiple task IDs from a commit message using the configured regex")
    void extractsTaskIds() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));
        stubCompare(1L, "main", "develop",
                comparisonJson("feat: PROJ-123 and PROJ-456 add dual-task feature"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).associatedTaskIds(),
                containsInAnyOrder("PROJ-123", "PROJ-456"));
    }

    @Test
    @DisplayName("Should return an empty task ID list when commit message has no match")
    void commitsWithNoTaskIdHaveEmptyList() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));
        stubCompare(1L, "main", "develop",
                comparisonJson("refactor: cleanup internals"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits().get(0).associatedTaskIds(), empty());
    }

    @Test
    @DisplayName("Should reflect the most recent commit date in lastCommitDate, not the first")
    void activeProjectInfoHasCorrectLastDate() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));

        String comparison = """
                { "commits": [
                  %s,
                  %s
                ]}
                """.formatted(
                commitJson("abc1", "feat: PROJ-1 older", "2024-01-10T08:00:00.000+00:00", "Alice", "http://g/1"),
                commitJson("abc2", "feat: PROJ-2 newer", "2024-03-20T15:30:00.000+00:00", "Bob",   "http://g/2")
        );
        stubCompare(1L, "main", "develop", comparison);

        String expectedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                .format(Date.from(Instant.parse("2024-03-20T15:30:00Z")));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.activeProjects(), hasSize(1));
        assertThat(result.activeProjects().get(0).lastCommitedOn(), is(expectedDate));
    }

    @Test
    @DisplayName("Should deduplicate task IDs repeated across multiple commits")
    void countsUniqueTaskIds() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));

        String comparison = """
                { "commits": [
                  %s,
                  %s,
                  %s
                ]}
                """.formatted(
                commitJson("abc1", "feat: PROJ-1 first commit", "2024-01-10T08:00:00.000+00:00", "Alice", "http://g/1"),
                commitJson("abc2", "fix: PROJ-1 follow-up",     "2024-01-11T08:00:00.000+00:00", "Alice", "http://g/2"),
                commitJson("abc3", "feat: PROJ-2 another task",  "2024-01-12T08:00:00.000+00:00", "Bob",   "http://g/3")
        );
        stubCompare(1L, "main", "develop", comparison);

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.activeProjects().get(0).totalRelatedTasks(), is("2"));
    }

    @Test
    @DisplayName("Should not list a project as active when all its commits are filtered out")
    void allFilteredCommitsNotListedAsActive() {
        String groupId = "10";
        stubGroup(groupId, "Group");
        stubGroupProjects(groupId, projectListJson(1L, "repo"));
        stubCompare(1L, "main", "develop",
                comparisonJson("Merge branch 'hotfix' into main"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.commits(), empty());
        assertThat(result.activeProjects(), empty());
    }

    @Test
    @DisplayName("Should populate SourceControlInfo with correct provider, URL and group name")
    void sourceControlInfoIsPopulatedCorrectly() {
        String groupId = "10";
        stubGroup(groupId, "My Team");
        stubGroupProjects(groupId, emptyList());

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(groupId), List.of("develop"), List.of("main"));

        assertThat(result.scInfo().providerName(), is("GitLab"));
        assertThat(result.scInfo().providerUrl(), is(wireMock.baseUrl()));
        assertThat(result.scInfo().groupName(), is("My Team"));
    }
}