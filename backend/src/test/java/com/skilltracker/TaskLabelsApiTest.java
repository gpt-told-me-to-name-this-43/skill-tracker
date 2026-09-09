package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TaskLabelsApiTest extends ApiTest {

    private String creatorAuth;
    private int taskId;

    @BeforeEach
    void createFixtures() throws Exception {
        creatorAuth = bearerFor("creator@example.com", "creator", "password-123");
        taskId = createTask(creatorAuth, """
                {"title": "Labelled task"}
                """).get("id").asInt();
    }

    @Test
    void theBaselineLabelsAreListedAlphabetically() throws Exception {
        mockMvc.perform(get("/api/v1/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].name").value("Backend"))
                .andExpect(jsonPath("$[0].created_at").isNotEmpty());
    }

    @Test
    void creatingALabelTrimsTheNameAndKeepsTheColour() throws Exception {
        createLabel("  Feature X  ", "\"#3B82F6\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Feature X"))
                .andExpect(jsonPath("$.color").value("#3B82F6"));
    }

    @Test
    void aDuplicateLabelNameConflictsCaseInsensitively() throws Exception {
        createLabel("Feature X", "null").andExpect(status().isCreated());

        createLabel("fEaTuRe x", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Label name already exists"));
    }

    /** The uniqueness lookup must compare literally rather than as a LIKE pattern. */
    @Test
    void wildcardCharactersInLabelNamesAreTreatedLiterally() throws Exception {
        createLabel("abc", "null").andExpect(status().isCreated());
        createLabel("a_c", "null").andExpect(status().isCreated());
        createLabel("Coverage", "null").andExpect(status().isCreated());
        createLabel("Cov%", "null").andExpect(status().isCreated());

        createLabel("cov%", "null").andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"red", "#FFF", "#GGGGGG", "3B82F6"})
    void anInvalidColourIsRejected(String color) throws Exception {
        createLabel("Feature X", "\"" + color + "\"").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aBlankLabelNameIsRejected() throws Exception {
        createLabel("   ", "null").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void settingLabelsReplacesTheWholeSet() throws Exception {
        int backend = labelId("Backend");
        int frontend = labelId("Frontend");

        setLabels("[%d]".formatted(backend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(1))
                .andExpect(jsonPath("$.labels[0].name").value("Backend"));

        setLabels("[%d]".formatted(frontend))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(1))
                .andExpect(jsonPath("$.labels[0].name").value("Frontend"));

        setLabels("[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(0));
    }

    @Test
    void anUnknownLabelIsNotFound() throws Exception {
        setLabels("[999999]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("One or more labels were not found"));
    }

    @Test
    void settingLabelsOnAnUnknownTaskIsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/999999/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label_ids": []}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateLabelIdsAreRejected() throws Exception {
        int backend = labelId("Backend");

        setLabels("[%d, %d]".formatted(backend, backend)).andExpect(status().isUnprocessableEntity());
    }

    /** Labels and skills are separate concepts: labelling must never touch the XP loop. */
    @Test
    void labellingNeverAwardsExperience() throws Exception {
        int backend = labelId("Backend");
        setLabels("[%d]".formatted(backend)).andExpect(status().isOk());
        setLabels("[]").andExpect(status().isOk());

        int creatorId = json(mockMvc.perform(get("/api/v1/tasks/" + taskId))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("creator_id")
                .asInt();
        mockMvc.perform(get("/api/v1/users/" + creatorId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions createLabel(String name, String color) throws Exception {
        return mockMvc.perform(
                post("/api/v1/labels").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "%s", "color": %s}
                        """.formatted(name, color)));
    }

    private ResultActions setLabels(String labelIds) throws Exception {
        return mockMvc.perform(put("/api/v1/tasks/" + taskId + "/labels")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label_ids": %s}
                        """.formatted(labelIds)));
    }

    private int labelId(String name) throws Exception {
        var labels = json(
                mockMvc.perform(get("/api/v1/labels")).andReturn().getResponse().getContentAsString());
        for (var label : labels) {
            if (label.get("name").asString().equals(name)) {
                return label.get("id").asInt();
            }
        }
        throw new IllegalStateException("Label not found: " + name);
    }
}
