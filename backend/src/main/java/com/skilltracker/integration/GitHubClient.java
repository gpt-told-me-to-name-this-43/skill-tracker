package com.skilltracker.integration;

import com.skilltracker.config.GitHubProperties;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.exception.ServiceUnavailableException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Reads issues of the configured repository through the GitHub REST API. */
@Component
public class GitHubClient implements GitHubIssueSource {

    private static final int PER_PAGE = 100;
    private static final String ACCEPT = "application/vnd.github+json";

    private final RestClient restClient;
    private final GitHubProperties properties;

    public GitHubClient(RestClient.Builder restClientBuilder, GitHubProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public List<GitHubIssue> fetchIssues() {
        List<GitHubIssue> issues = new ArrayList<>();

        int page = 1;
        while (true) {
            JsonNode payload = requestPage(page);
            int received = payload.isArray() ? payload.size() : 0;

            for (JsonNode item : payload) {
                // The endpoint also returns pull requests; they carry a pull_request key.
                if (!item.has("pull_request")) {
                    issues.add(parseIssue(item));
                }
            }

            if (received < PER_PAGE) {
                return issues;
            }
            page++;
        }
    }

    private JsonNode requestPage(int page) {
        ResponseEntity<JsonNode> response;
        try {
            response = restClient
                    .get()
                    .uri(issuesUri(page))
                    .accept(MediaType.parseMediaType(ACCEPT))
                    .headers(this::applyAuthorization)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {})
                    .toEntity(JsonNode.class);
        } catch (ResourceAccessException unreachable) {
            throw new ServiceUnavailableException("GitHub is unreachable: check network and proxy settings");
        }

        raiseForStatus(response.getStatusCode().value());
        JsonNode body = response.getBody();
        return body == null ? tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode() : body;
    }

    /**
     * Built as a whole URI rather than through a template: the { owner/name} slug carries a
     * slash that template expansion would percent-encode into a path segment GitHub does not know.
     */
    private URI issuesUri(int page) {
        return URI.create(baseUrl() + "/repos/" + properties.repo() + "/issues" + "?state=all&per_page=" + PER_PAGE
                + "&page=" + page);
    }

    private void applyAuthorization(HttpHeaders headers) {
        if (properties.hasToken()) {
            headers.setBearerAuth(properties.token());
        }
    }

    private void raiseForStatus(int status) {
        if (status == 404) {
            throw new NotFoundException("GitHub repository " + properties.repo() + " not found");
        }
        if (status == 401 || status == 403) {
            throw new BadRequestException("GitHub rejected the request: check token and rate limits");
        }
        if (status == 429 || status >= 500) {
            throw new ServiceUnavailableException("GitHub is temporarily unavailable, try again later");
        }
        if (status >= 400) {
            throw new BadRequestException("GitHub API error: HTTP " + status);
        }
    }

    private String baseUrl() {
        String apiUrl = properties.apiUrl();
        return apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    private GitHubIssue parseIssue(JsonNode payload) {
        JsonNode assignee = payload.get("assignee");
        List<String> labels = new ArrayList<>();
        JsonNode labelNodes = payload.get("labels");
        if (labelNodes != null && labelNodes.isArray()) {
            labelNodes.forEach(label -> labels.add(label.path("name").asString()));
        }

        return new GitHubIssue(
                payload.path("number").asInt(),
                payload.path("title").asString(),
                payload.hasNonNull("body") ? payload.get("body").asString() : null,
                payload.path("state").asString(),
                assignee != null && !assignee.isNull() ? assignee.path("login").asString() : null,
                labels);
    }
}
