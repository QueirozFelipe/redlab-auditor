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
class GitHubAdapterTest {

    static WireMockServer wireMock;
    GitHubAdapter adapter;

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
        adapter = new GitHubAdapter();
    }

    private Profile profile(String owner,
                            String taskRegex,
                            Set<Long> ignoreIds,
                            int rateLimit,
                            List<String> sourceBranches,
                            List<String> targetBranches) {
        return new Profile(
                "test-profile",
                ProjectManagerType.JIRA,
                SourceControlType.GITHUB,
                "http://pm.local",
                "pm-token",
                Set.of(),
                wireMock.baseUrl(),
                "fake-token",
                owner,
                rateLimit,
                ignoreIds,
                sourceBranches,
                targetBranches,
                taskRegex
        );
    }

    private Profile defaultProfile(String owner) {
        return profile(owner, "([A-Z]+-\\d+)", Set.of(), 10,
                List.of("develop"), List.of("main"));
    }

    private String repoListJson(long id, String name) {
        return """
                [
                  {
                    "id": %d,
                    "name": "%s",
                    "full_name": "org/%s"
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
                    "sha-" + i, messages[i], "2024-01-15T12:00:00Z",
                    "Dev " + i, "http://github.com/c/" + i));
        }
        return "{ \"commits\": [ %s ] }".formatted(commits);
    }

    private String commitJson(String sha, String message, String date, String author, String url) {
        return """
                {
                  "sha": "%s",
                  "html_url": "%s",
                  "commit": {
                    "message": "%s",
                    "author":    { "name": "%s" },
                    "committer": { "date": "%s" }
                  }
                }
                """.formatted(sha, url, message, author, date);
    }

    /** GET /orgs/{owner}/repos — page 1 returns repos, page 2 returns []. */
    private void stubOrgRepos(String owner, String reposJson) {
        wireMock.stubFor(get(urlPathEqualTo("/orgs/" + owner + "/repos"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(reposJson)));

        wireMock.stubFor(get(urlPathEqualTo("/orgs/" + owner + "/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(emptyList())));
    }

    /** GET /repos/{owner}/{repo}/compare/{base}...{head} → 200 */
    private void stubCompare(String owner, String repo, String base, String head, String body) {
        wireMock.stubFor(get(urlPathEqualTo(
                "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head))
                .willReturn(okJson(body)));
    }

    /** GET /repos/{owner}/{repo}/compare/{base}...{head} → 404 */
    private void stubCompareNotFound(String owner, String repo, String base, String head) {
        wireMock.stubFor(get(urlPathEqualTo(
                "/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head))
                .willReturn(notFound().withBody("{\"message\":\"Not Found\"}")));
    }

    @Test
    @DisplayName("Should fetch group name and all projects paginating correctly")
    void fetchesOrgRepos() {
        String owner = "my-org";
        stubOrgRepos(owner, repoListJson(1L, "repo-a"));
        stubCompare(owner, "repo-a", "main", "develop", comparisonJson("feat: PROJ-1 add login"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.scInfo().totalProjects(), is(1));
    }

    @Test
    @DisplayName("Should fallback to user endpoint when org returns 404 ")
    void fallsBackToUserRepos() {
        String owner = "my-user";

        wireMock.stubFor(get(urlPathEqualTo("/orgs/" + owner + "/repos"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(notFound().withBody("{\"message\":\"Not Found\"}")));

        wireMock.stubFor(get(urlPathEqualTo("/users/" + owner + "/repos"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(repoListJson(42L, "user-repo"))));
        wireMock.stubFor(get(urlPathEqualTo("/users/" + owner + "/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(emptyList())));

        stubCompare(owner, "user-repo", "main", "develop", comparisonJson("fix: PROJ-2 bug"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.scInfo().totalProjects(), is(1));
    }

    @Test
    @DisplayName("Should not audit projects whose ID is listed in projectsToIgnore")
    void skipsIgnoredProjects() {
        String owner = "org";
        long ignoredId = 99L;

        wireMock.stubFor(get(urlPathEqualTo("/orgs/" + owner + "/repos"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("""
                        [
                          {"id": 1,  "name": "repo-ok"},
                          {"id": %d, "name": "repo-ignored"}
                        ]
                        """.formatted(ignoredId))));
        wireMock.stubFor(get(urlPathEqualTo("/orgs/" + owner + "/repos"))
                .withQueryParam("page", equalTo("2"))
                .willReturn(okJson(emptyList())));

        stubCompare(owner, "repo-ok", "main", "develop", comparisonJson("feat: PROJ-1 stuff"));

        Profile p = profile(owner, "([A-Z]+-\\d+)", Set.of(ignoredId), 10,
                List.of("develop"), List.of("main"));
        SourceControlResult result = adapter.compareBranches(p, List.of("develop"), List.of("main"));

        assertThat(result.ignoredByUserProjects(), hasItem("repo-ignored"));
        assertThat(result.ignoredByUserProjects(), not(hasItem("repo-ok")));
    }

    @Test
    @DisplayName("Should return commits that are ahead of the target branch")
    void returnsAheadCommits() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "my-repo"));
        stubCompare(owner, "my-repo", "main", "develop",
                comparisonJson("feat: PROJ-42 implement feature", "fix: PROJ-43 fix bug"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(2));
        result.commits().forEach(c -> assertThat(c.projectName(), is("my-repo")));
    }

    @Test
    @DisplayName("Should track projects with no valid branch combination in missingBranchProjects")
    void tracksMissingBranchProjects() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "no-branch-repo"));
        stubCompareNotFound(owner, "no-branch-repo", "main", "develop");

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.missingBranchProjects(), hasItem("no-branch-repo"));
        assertThat(result.commits(), empty());
    }

    @Test
    @DisplayName("Should try all source×target branch combinations until a valid one is found")
    void triesAllBranchCombinations() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "multi-branch-repo"));

        stubCompareNotFound(owner, "multi-branch-repo", "main", "feature");
        stubCompare(owner, "multi-branch-repo", "main", "develop",
                comparisonJson("feat: PROJ-1 works on develop"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("feature", "develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.missingBranchProjects(), empty());
    }

    @Test
    @DisplayName("Should not include merge commits in the result")
    void filtersMergeCommits() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));
        stubCompare(owner, "repo", "main", "develop", comparisonJson(
                "Merge branch 'feature' into develop",
                "Merge pull request #12 from org/feature",
                "feat: PROJ-1 actual work"
        ));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).message(), is("feat: PROJ-1 actual work"));
    }

    @Test
    @DisplayName("Should not include chore:release commits in the result")
    void filtersReleaseCommits() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));
        stubCompare(owner, "repo", "main", "develop", comparisonJson(
                "chore: release 1.2.3",
                "chore:release v2.0",
                "fix: PROJ-5 real fix"
        ));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).message(), containsString("PROJ-5"));
    }

    @Test
    @DisplayName("Should extract multiple task IDs from a commit message using the configured regex")
    void extractsTaskIds() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));
        stubCompare(owner, "repo", "main", "develop",
                comparisonJson("feat: PROJ-123 and PROJ-456 add dual-task feature"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits(), hasSize(1));
        assertThat(result.commits().get(0).associatedTaskIds(),
                containsInAnyOrder("PROJ-123", "PROJ-456"));
    }

    @Test
    @DisplayName("Should return an empty task ID list when commit message has no match")
    void commitsWithNoTaskIdHaveEmptyList() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));
        stubCompare(owner, "repo", "main", "develop",
                comparisonJson("refactor: cleanup internals"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits().get(0).associatedTaskIds(), empty());
    }

    @Test
    @DisplayName("Should reflect the most recent commit date in lastCommitDate, not the first")
    void activeProjectInfoHasCorrectLastDate() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));

        String comparison = """
            { "commits": [
              %s,
              %s
            ]}
            """.formatted(
                commitJson("sha-1", "feat: PROJ-1 older", "2024-01-10T08:00:00Z", "Alice", "http://g/1"),
                commitJson("sha-2", "feat: PROJ-2 newer", "2024-03-20T15:30:00Z", "Bob",   "http://g/2")
        );
        stubCompare(owner, "repo", "main", "develop", comparison);

        String expectedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm")
                .format(Date.from(Instant.parse("2024-03-20T15:30:00Z")));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.activeProjects(), hasSize(1));
        assertThat(result.activeProjects().get(0).lastCommitedOn(), is(expectedDate));
    }

    @Test
    @DisplayName("Should deduplicate task IDs repeated across multiple commits")
    void countsUniqueTaskIds() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));

        String comparison = """
                { "commits": [
                  %s,
                  %s,
                  %s
                ]}
                """.formatted(
                commitJson("sha-1", "feat: PROJ-1 first commit", "2024-01-10T08:00:00Z", "Alice", "http://g/1"),
                commitJson("sha-2", "fix: PROJ-1 follow-up",     "2024-01-11T08:00:00Z", "Alice", "http://g/2"),
                commitJson("sha-3", "feat: PROJ-2 another task",  "2024-01-12T08:00:00Z", "Bob",   "http://g/3")
        );
        stubCompare(owner, "repo", "main", "develop", comparison);

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.activeProjects().get(0).totalRelatedTasks(), is("2"));
    }

    @Test
    @DisplayName("Org without repos should return empty result without errors")
    void noRepositoriesReturnsEmptyResult() {
        String owner = "empty-org";
        wireMock.stubFor(get(urlPathEqualTo("/orgs/" + owner + "/repos"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson(emptyList())));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits(), empty());
        assertThat(result.activeProjects(), empty());
        assertThat(result.scInfo().totalProjects(), is(0));
    }

    @Test
    @DisplayName("Should not list a project as active when all its commits are filtered out")
    void allFilteredCommitsNotListedAsActive() {
        String owner = "org";
        stubOrgRepos(owner, repoListJson(1L, "repo"));
        stubCompare(owner, "repo", "main", "develop",
                comparisonJson("Merge pull request #10 from org/main"));

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.commits(), empty());
        assertThat(result.activeProjects(), empty());
    }

    @Test
    @DisplayName("Should populate SourceControlInfo with correct provider, URL and owner/organization name")
    void sourceControlInfoIsPopulatedCorrectly() {
        String owner = "org";
        stubOrgRepos(owner, emptyList());

        SourceControlResult result = adapter.compareBranches(
                defaultProfile(owner), List.of("develop"), List.of("main"));

        assertThat(result.scInfo().providerName(), is("GitHub"));
        assertThat(result.scInfo().providerUrl(), is(wireMock.baseUrl()));
        assertThat(result.scInfo().groupName(), is(owner));
    }
}