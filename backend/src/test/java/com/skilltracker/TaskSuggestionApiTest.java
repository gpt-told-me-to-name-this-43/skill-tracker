package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.integration.RawTaskSuggestion;
import com.skilltracker.integration.SuggestionCandidate;
import com.skilltracker.integration.TaskSuggestionSource;
import com.skilltracker.support.ApiTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Suggestions are validated against the database, so an ML stub is enough to drive the endpoint. */
@Import(TaskSuggestionApiTest.StubSuggestionSourceConfiguration.class)
class TaskSuggestionApiTest extends ApiTest {

    @TestConfiguration
    static class StubSuggestionSourceConfiguration {
        @Bean
        StubSuggestionSource stubSuggestionSource() {
            return new StubSuggestionSource();
        }
    }

    /** Records what the service asked for and replays a canned answer. */
    static class StubSuggestionSource implements TaskSuggestionSource {
        RawTaskSuggestion answer = RawTaskSuggestion.empty();
        final List<List<SuggestionCandidate>> labelCandidates = new ArrayList<>();
        final List<List<SuggestionCandidate>> skillCandidates = new ArrayList<>();
        String lastTitle;
        String lastDescription;

        @Override
        public RawTaskSuggestion suggestFields(
                String title, String description, List<SuggestionCandidate> labels, List<SuggestionCandidate> skills) {
            lastTitle = title;
            lastDescription = description;
            labelCandidates.add(labels);
            skillCandidates.add(skills);
            return answer;
        }
    }

    @Autowired
    private StubSuggestionSource source;

    private String auth;

    @BeforeEach
    void createUser() throws Exception {
        auth = bearerFor("analyst@example.com", "analyst", "password-123");
        source.answer = RawTaskSuggestion.empty();
        source.labelCandidates.clear();
        source.skillCandidates.clear();
    }

    @Test
    void suggestionsAreReturnedAsLabelsSkillsAndADeadline() throws Exception {
        int skillId = createSkill("Python").get("id").asInt();
        int labelId = firstLabelId();
        source.answer =
                new RawTaskSuggestion(4, 5, List.of(labelId), List.of(new RawTaskSuggestion.SkillReward(skillId, 150)));

        String body = analyze("""
                {"title": "Fix login bug", "description": "Users cannot log in"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value(4))
                .andExpect(jsonPath("$.labels.length()").value(1))
                .andExpect(jsonPath("$.labels[0].id").value(labelId))
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].skill.name").value("Python"))
                .andExpect(jsonPath("$.skills[0].exp_reward").value(150))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The model estimates in days; the concrete date is derived here and stays offset-aware.
        OffsetDateTime deadline =
                OffsetDateTime.parse(json(body).get("deadline").asString());
        assertThat(deadline).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusDays(4));
        assertThat(deadline).isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusDays(6));

        assertThat(source.lastTitle).isEqualTo("Fix login bug");
        assertThat(source.lastDescription).isEqualTo("Users cannot log in");
    }

    @Test
    void unknownIdsFromTheModelAreDropped() throws Exception {
        int skillId = createSkill("Python").get("id").asInt();
        source.answer = new RawTaskSuggestion(
                3,
                null,
                List.of(999999),
                List.of(new RawTaskSuggestion.SkillReward(skillId, 50), new RawTaskSuggestion.SkillReward(888888, 50)));

        analyze("""
                {"title": "Fix login bug"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(0))
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].skill.id").value(skillId))
                .andExpect(jsonPath("$.deadline").doesNotExist());
    }

    @Test
    void difficultyAndRewardsAreClamped() throws Exception {
        int skillId = createSkill("Python").get("id").asInt();

        source.answer =
                new RawTaskSuggestion(10, null, List.of(), List.of(new RawTaskSuggestion.SkillReward(skillId, 5000)));
        analyze("""
                {"title": "Fix login bug"}
                """)
                .andExpect(jsonPath("$.difficulty").value(5))
                .andExpect(jsonPath("$.skills[0].exp_reward").value(1000));

        source.answer =
                new RawTaskSuggestion(0, null, List.of(), List.of(new RawTaskSuggestion.SkillReward(skillId, -5)));
        analyze("""
                {"title": "Fix login bug"}
                """)
                .andExpect(jsonPath("$.difficulty").value(1))
                .andExpect(jsonPath("$.skills[0].exp_reward").value(1));

        source.answer = new RawTaskSuggestion(null, null, List.of(), List.of());
        analyze("""
                {"title": "Fix login bug"}
                """).andExpect(jsonPath("$.difficulty").value(3));
    }

    @Test
    void duplicateSkillsKeepOnlyTheFirstReward() throws Exception {
        int skillId = createSkill("Python").get("id").asInt();
        source.answer = new RawTaskSuggestion(
                3,
                null,
                List.of(),
                List.of(
                        new RawTaskSuggestion.SkillReward(skillId, 100),
                        new RawTaskSuggestion.SkillReward(skillId, 200)));

        analyze("""
                {"title": "Fix login bug"}
                """)
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].exp_reward").value(100));
    }

    @Test
    void estimatedDaysAreClamped() throws Exception {
        source.answer = new RawTaskSuggestion(3, 9000, List.of(), List.of());

        String body = analyze("""
                {"title": "Fix login bug"}
                """).andReturn().getResponse().getContentAsString();

        OffsetDateTime deadline =
                OffsetDateTime.parse(json(body).get("deadline").asString());
        assertThat(deadline).isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusDays(366));
        assertThat(deadline).isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusDays(364));
    }

    @Test
    void theModelSeesEveryCandidateInTheCatalogue() throws Exception {
        createSkill("Python");
        createSkill("React");
        source.answer = RawTaskSuggestion.empty();

        analyze("""
                {"title": "Fix login bug"}
                """).andExpect(status().isOk());

        assertThat(source.skillCandidates.getLast())
                .extracting(SuggestionCandidate::name)
                .containsExactly("Python", "React");
        assertThat(source.labelCandidates.getLast()).hasSize(9);
    }

    @Test
    void analyzingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Fix login bug"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBlankTitleIsAValidationError() throws Exception {
        analyze("""
                {"title": "   "}
                """).andExpect(status().isUnprocessableEntity());
    }

    private org.springframework.test.web.servlet.ResultActions analyze(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks/analyze")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int firstLabelId() throws Exception {
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/labels"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(body).get(0).get("id").asInt();
    }
}
