package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class TaskLintApiTest extends ApiTest {

    private String auth;

    @BeforeEach
    void createUser() throws Exception {
        auth = bearerFor("creator@example.com", "creator", "password-123");
    }

    @Test
    void aSparseTaskReportsItsWarningsInOrder() throws Exception {
        int taskId = createTask(auth, """
                {"title": "Add Kanban"}
                """).get("id").asInt();

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/lint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(taskId))
                .andExpect(jsonPath("$.warnings.length()").value(4))
                .andExpect(jsonPath("$.warnings[0].code").value("description_missing"))
                .andExpect(jsonPath("$.warnings[1].code").value("no_skill_rewards"))
                .andExpect(jsonPath("$.warnings[2].code").value("no_deadline"))
                .andExpect(jsonPath("$.warnings[3].code").value("no_labels"))
                .andExpect(jsonPath("$.warnings[0].severity").value("warning"))
                .andExpect(jsonPath("$.warnings[0].field").value("description"));
    }

    @Test
    void aCompleteTaskReportsNothing() throws Exception {
        String deadline = LocalDateTime.now(ZoneOffset.UTC).plusDays(7) + "Z";
        int taskId = createTask(auth, """
                        {"title": "Add Kanban board", "description": "A long enough description of the kanban work.",
                         "deadline": "%s"}
                        """.formatted(deadline)).get("id").asInt();

        int skillId = createSkill("Python").get("id").asInt();
        mockMvc.perform(put("/api/v1/tasks/" + taskId + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills": [{"skill_id": %d, "exp_reward": 50}]}
                                """.formatted(skillId)))
                .andExpect(status().isOk());

        var labels = json(
                mockMvc.perform(get("/api/v1/labels")).andReturn().getResponse().getContentAsString());
        mockMvc.perform(put("/api/v1/tasks/" + taskId + "/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label_ids": [%d]}
                                """.formatted(labels.get(0).get("id").asInt())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/lint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void lintingAnUnknownTaskIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/999999/lint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    /** The endpoint is public, like the sibling task reads. */
    @Test
    void lintingDoesNotRequireAuthentication() throws Exception {
        int taskId = createTask(auth, """
                {"title": "Add Kanban"}
                """).get("id").asInt();

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/lint").header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isOk());
    }
}
