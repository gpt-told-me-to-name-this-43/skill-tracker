package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.skilltracker.config.OpenRouterProperties;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.exception.ServiceUnavailableException;
import com.skilltracker.integration.OpenRouterClient;
import com.skilltracker.integration.RawTaskSuggestion;
import com.skilltracker.integration.SuggestionCandidate;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Exercises the OpenRouter response handling without touching the network. */
class OpenRouterClientTest {

    private static final String API_URL = "https://or.test/api/v1";
    private static final String COMPLETIONS = API_URL + "/chat/completions";

    private static final List<SuggestionCandidate> LABELS =
            List.of(new SuggestionCandidate(1, "Backend"), new SuggestionCandidate(2, "Frontend"));
    private static final List<SuggestionCandidate> SKILLS =
            List.of(new SuggestionCandidate(10, "Python"), new SuggestionCandidate(11, "React"));

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private record Fixture(OpenRouterClient client, MockRestServiceServer server) {}

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterProperties properties = new OpenRouterProperties("secret-key", "test/model", API_URL);
        return new Fixture(new OpenRouterClient(builder, properties, objectMapper), server);
    }

    private String completion(String content) {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
    }

    private RawTaskSuggestion suggest(Fixture fixture) {
        return fixture.client().suggestFields("Fix login bug", "Users cannot log in", LABELS, SKILLS);
    }

    @Test
    void parsesAWellFormedSuggestion() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(completion("""
                                {"difficulty": 4, "estimated_days": 7, "label_ids": [1],
                                 "skills": [{"skill_id": 10, "exp_reward": 120}]}
                                """), MediaType.APPLICATION_JSON));

        RawTaskSuggestion raw = suggest(fixture);

        assertThat(raw.difficulty()).isEqualTo(4);
        assertThat(raw.estimatedDays()).isEqualTo(7);
        assertThat(raw.labelIds()).containsExactly(1);
        assertThat(raw.skills()).containsExactly(new RawTaskSuggestion.SkillReward(10, 120));
    }

    @Test
    void stripsAMarkdownCodeFence() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(
                        completion("```json\n{\"difficulty\": 2, \"label_ids\": [2], \"skills\": []}\n```"),
                        MediaType.APPLICATION_JSON));

        RawTaskSuggestion raw = suggest(fixture);

        assertThat(raw.difficulty()).isEqualTo(2);
        assertThat(raw.labelIds()).containsExactly(2);
        assertThat(raw.skills()).isEmpty();
    }

    @Test
    void sendsTheCredentialModelAndCandidates() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(request -> {
                    JsonNode body = objectMapper.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.get("model").asString()).isEqualTo("test/model");
                    assertThat(body.get("response_format").get("type").asString())
                            .isEqualTo("json_object");
                    assertThat(body.get("messages").get(0).get("role").asString())
                            .isEqualTo("system");

                    JsonNode userPayload = objectMapper.readTree(
                            body.get("messages").get(1).get("content").asString());
                    assertThat(userPayload.get("title").asString()).isEqualTo("Fix login bug");
                    assertThat(userPayload.get("description").asString()).isEqualTo("Users cannot log in");
                    assertThat(userPayload.get("label_candidates")).hasSize(2);
                    assertThat(userPayload.get("skill_candidates")).hasSize(2);
                })
                .andRespond(withSuccess(
                        completion("{\"difficulty\": 3, \"label_ids\": [], \"skills\": []}"),
                        MediaType.APPLICATION_JSON));

        suggest(fixture);
        fixture.server().verify();
    }

    @Test
    void ignoresValuesThatAreNotIntegers() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(completion("""
                                {"difficulty": "hard", "estimated_days": "a week", "label_ids": [1, "two", null],
                                 "skills": [{"skill_id": 10, "exp_reward": 100},
                                            {"skill_id": "React", "exp_reward": 50},
                                            {"skill_id": 11}, "not-a-dict"]}
                                """), MediaType.APPLICATION_JSON));

        RawTaskSuggestion raw = suggest(fixture);

        assertThat(raw.difficulty()).isNull();
        assertThat(raw.estimatedDays()).isNull();
        assertThat(raw.labelIds()).containsExactly(1);
        assertThat(raw.skills()).containsExactly(new RawTaskSuggestion.SkillReward(10, 100));
    }

    /** JSON booleans are not integers, even though Python would treat them as such. */
    @Test
    void ignoresBooleanValues() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(completion("""
                                {"difficulty": true, "estimated_days": false, "label_ids": [true, 1],
                                 "skills": [{"skill_id": true, "exp_reward": 100},
                                            {"skill_id": 10, "exp_reward": false}]}
                                """), MediaType.APPLICATION_JSON));

        RawTaskSuggestion raw = suggest(fixture);

        assertThat(raw.difficulty()).isNull();
        assertThat(raw.estimatedDays()).isNull();
        assertThat(raw.labelIds()).containsExactly(1);
        assertThat(raw.skills()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void clientErrorsBecomeABadRequest(int status) {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body("{\"error\": \"denied\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> suggest(fixture)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500})
    void temporaryFailuresBecomeServiceUnavailable(int status) {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .body("{\"error\": \"unavailable\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> suggest(fixture)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void aConnectionFailureBecomesServiceUnavailable() {
        Fixture fixture = fixture();
        fixture.server().expect(requestTo(COMPLETIONS)).andRespond(request -> {
            throw new IOException("All connection attempts failed");
        });

        assertThatThrownBy(() -> suggest(fixture))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void contentThatIsNotJsonIsRejected() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(completion("difficulty is about 3 or so"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> suggest(fixture))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("ML provider returned malformed JSON");
    }

    @Test
    void anUnexpectedEnvelopeIsRejected() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess("{\"unexpected\": \"shape\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> suggest(fixture))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("ML provider returned unexpected response shape");
    }

    @Test
    void nonObjectContentIsRejected() {
        Fixture fixture = fixture();
        fixture.server()
                .expect(requestTo(COMPLETIONS))
                .andRespond(withSuccess(completion("[\"not\", \"an\", \"object\"]"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> suggest(fixture)).isInstanceOf(BadRequestException.class);
    }
}
