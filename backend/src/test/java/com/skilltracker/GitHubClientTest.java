package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.skilltracker.config.GitHubProperties;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.exception.ServiceUnavailableException;
import com.skilltracker.integration.GitHubClient;
import com.skilltracker.integration.GitHubIssue;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Exercises the GitHub REST mapping without touching the network. */
class GitHubClientTest {

    private static final String API_URL = "https://gh.test";
    private static final String REPO = "acme/widgets";

    private record Fixture(GitHubClient client, MockRestServiceServer server) {}

    private Fixture fixture(String token) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new GitHubClient(builder, new GitHubProperties(REPO, API_URL, token)), server);
    }

    private String issue(int number, String extra) {
        return """
                {"number": %d, "title": "Issue %d", "body": "Body %d", "state": "open",
                 "assignee": null, "labels": []%s}
                """.formatted(number, number, number, extra);
    }

    @Test
    void parsesIssueFields() {
        Fixture fixture = fixture(null);
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andRespond(withSuccess("""
                        [{"number": 7, "title": "Fix login", "body": "Steps to reproduce", "state": "closed",
                          "assignee": {"login": "octocat"}, "labels": [{"name": "bug"}, {"name": "Backend"}]},
                         {"number": 8, "title": "Issue 8", "body": null, "state": "open",
                          "assignee": null, "labels": []}]
                        """, MediaType.APPLICATION_JSON));

        List<GitHubIssue> issues = fixture.client().fetchIssues();

        assertThat(issues).hasSize(2);
        assertThat(issues.get(0))
                .isEqualTo(new GitHubIssue(
                        7, "Fix login", "Steps to reproduce", "closed", "octocat", List.of("bug", "Backend")));
        assertThat(issues.get(1).assigneeLogin()).isNull();
        assertThat(issues.get(1).body()).isNull();
    }

    /** The issues endpoint also returns pull requests, which are not tasks. */
    @Test
    void filtersOutPullRequests() {
        Fixture fixture = fixture(null);
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andRespond(withSuccess(
                        "[" + issue(1, "") + "," + issue(2, ", \"pull_request\": {\"url\": \"https://gh.test/pr/2\"}")
                                + "]",
                        MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchIssues())
                .extracting(GitHubIssue::number)
                .containsExactly(1);
    }

    @Test
    void paginatesUntilAShortPage() {
        Fixture fixture = fixture(null);
        String fullPage = IntStream.rangeClosed(1, 100)
                .mapToObj(number -> issue(number, ""))
                .collect(Collectors.joining(",", "[", "]"));

        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andRespond(withSuccess(fullPage, MediaType.APPLICATION_JSON));
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=2"))
                .andRespond(withSuccess("[" + issue(101, "") + "]", MediaType.APPLICATION_JSON));

        assertThat(fixture.client().fetchIssues()).hasSize(101);
        fixture.server().verify();
    }

    @Test
    void sendsTheTokenWhenOneIsConfigured() {
        Fixture fixture = fixture("secret-token");
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        fixture.client().fetchIssues();
        fixture.server().verify();
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutAToken() {
        Fixture fixture = fixture("");
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization"))
                        .isNull())
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        fixture.client().fetchIssues();
        fixture.server().verify();
    }

    @Test
    void aMissingRepositoryIsNotFound() {
        Fixture fixture = fixture(null);
        fixture.server()
                .expect(
                        ExpectedCount.once(),
                        requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchIssues())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("GitHub repository " + REPO + " not found");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void rejectedCredentialsBecomeABadRequest(int status) {
        Fixture fixture = fixture(null);
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchIssues()).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void temporaryFailuresBecomeServiceUnavailable(int status) {
        Fixture fixture = fixture(null);
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().fetchIssues()).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void aConnectionFailureBecomesServiceUnavailable() {
        Fixture fixture = fixture(null);
        fixture.server()
                .expect(requestTo(API_URL + "/repos/" + REPO + "/issues?state=all&per_page=100&page=1"))
                .andRespond(request -> {
                    throw new IOException("Proxy is unavailable");
                });

        assertThatThrownBy(() -> fixture.client().fetchIssues())
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unreachable");
    }
}
