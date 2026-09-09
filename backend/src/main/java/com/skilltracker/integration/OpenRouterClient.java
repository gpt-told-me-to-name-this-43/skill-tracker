package com.skilltracker.integration;

import com.skilltracker.config.OpenRouterConfiguredCondition;
import com.skilltracker.config.OpenRouterProperties;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.exception.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Asks OpenRouter to suggest task fields. Registered only when {@code OPENROUTER_API_KEY} is set, so
 * an unconfigured deployment answers 503 exactly as before.
 */
@Component
@Conditional(OpenRouterConfiguredCondition.class)
public class OpenRouterClient implements TaskSuggestionSource {

    private static final String SYSTEM_PROMPT =
            "You are an assistant for a task tracker. Given a task title and description, "
                    + "suggest a difficulty (integer 1-5), a realistic estimate of days needed to "
                    + "complete the task (integer 1-365), matching labels and relevant skills with "
                    + "an XP reward (integer 1-1000) proportional to the difficulty. "
                    + "Pick labels and skills ONLY from the provided candidates, by their ids. "
                    + "Respond with strict JSON, no prose, in the shape: "
                    + "{\"difficulty\": 3, \"estimated_days\": 7, \"label_ids\": [1, 2], "
                    + "\"skills\": [{\"skill_id\": 1, \"exp_reward\": 100}]}";

    private static final String UNEXPECTED_SHAPE = "ML provider returned unexpected response shape";

    private final RestClient restClient;
    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper;

    public OpenRouterClient(
            @Qualifier("mlRestClientBuilder") RestClient.Builder restClientBuilder,
            OpenRouterProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RawTaskSuggestion suggestFields(
            String title, String description, List<SuggestionCandidate> labels, List<SuggestionCandidate> skills) {
        String userMessage = objectMapper.writeValueAsString(Map.of(
                "title",
                title,
                "description",
                description == null ? "" : description,
                "label_candidates",
                candidates(labels),
                "skill_candidates",
                candidates(skills)));

        Map<String, Object> body = Map.of(
                "model",
                properties.model(),
                "messages",
                List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)),
                "response_format",
                Map.of("type", "json_object"));

        ResponseEntity<String> response;
        try {
            response = restClient
                    .post()
                    .uri(baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {})
                    .toEntity(String.class);
        } catch (ResourceAccessException unreachable) {
            throw new ServiceUnavailableException("ML provider is unreachable: check network and proxy settings");
        }

        raiseForStatus(response.getStatusCode().value());
        return parseSuggestion(extractContent(response.getBody()));
    }

    private List<Map<String, Object>> candidates(List<SuggestionCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> Map.<String, Object>of("id", candidate.id(), "name", candidate.name()))
                .toList();
    }

    private void raiseForStatus(int status) {
        if (status == 401 || status == 403) {
            throw new BadRequestException("ML provider rejected the request: check the API key");
        }
        if (status == 429 || status >= 500) {
            throw new ServiceUnavailableException("ML provider is temporarily unavailable, try again later");
        }
        if (status >= 400) {
            throw new BadRequestException("ML provider rejected the request: HTTP " + status);
        }
    }

    private String extractContent(String body) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(body == null ? "" : body);
        } catch (JacksonException malformed) {
            throw new BadRequestException(UNEXPECTED_SHAPE);
        }

        JsonNode content = payload.path("choices").path(0).path("message").path("content");
        if (!content.isString()) {
            throw new BadRequestException(UNEXPECTED_SHAPE);
        }
        return content.asString();
    }

    RawTaskSuggestion parseSuggestion(String content) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(stripCodeFence(content));
        } catch (JacksonException malformed) {
            throw new BadRequestException("ML provider returned malformed JSON");
        }
        if (!payload.isObject()) {
            throw new BadRequestException(UNEXPECTED_SHAPE);
        }

        List<Integer> labelIds = new ArrayList<>();
        for (JsonNode item : payload.path("label_ids")) {
            if (item.isIntegralNumber()) {
                labelIds.add(item.asInt());
            }
        }

        List<RawTaskSuggestion.SkillReward> skills = new ArrayList<>();
        for (JsonNode item : payload.path("skills")) {
            JsonNode skillId = item.path("skill_id");
            JsonNode expReward = item.path("exp_reward");
            if (item.isObject() && skillId.isIntegralNumber() && expReward.isIntegralNumber()) {
                skills.add(new RawTaskSuggestion.SkillReward(skillId.asInt(), expReward.asInt()));
            }
        }

        return new RawTaskSuggestion(
                integerOrNull(payload.path("difficulty")),
                integerOrNull(payload.path("estimated_days")),
                labelIds,
                skills);
    }

    private Integer integerOrNull(JsonNode node) {
        return node.isIntegralNumber() ? node.asInt() : null;
    }

    private String stripCodeFence(String content) {
        String trimmed = content.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewline = trimmed.indexOf('\n');
        String withoutOpeningFence = firstNewline == -1 ? "" : trimmed.substring(firstNewline + 1);
        int closingFence = withoutOpeningFence.lastIndexOf("```");
        return (closingFence == -1 ? withoutOpeningFence : withoutOpeningFence.substring(0, closingFence)).strip();
    }

    private String baseUrl() {
        String apiUrl = properties.apiUrl();
        return apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }
}
