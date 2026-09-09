package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/**
 * The React client reads snake_case fields and naive timestamps. A camelCase slip anywhere would be
 * invisible in Java but would break every consumer, so the whole payload shape is asserted here.
 */
class JsonWireFormatTest extends ApiTest {

    @Test
    void taskPayloadsUseSnakeCaseAndNaiveTimestamps() throws Exception {
        String auth = bearerFor("creator@example.com", "creator", "password-123");
        JsonNode task = createTask(auth, """
                {"title": "Wire format", "description": "text", "deadline": "2099-01-02T03:04:05Z"}
                """);

        assertThat(fieldNames(task))
                .containsExactlyInAnyOrder(
                        "id",
                        "title",
                        "status",
                        "difficulty",
                        "deadline",
                        "creator",
                        "assignee",
                        "labels",
                        "attachments_count",
                        "related_tasks_count",
                        "github_issue_number",
                        "github_url",
                        "created_at",
                        "updated_at",
                        "description",
                        "attachments",
                        "related_tasks",
                        "creator_id",
                        "assignee_id",
                        "approved_by_id",
                        "approved_at");

        // Timestamps are naive UTC, exactly as they are stored, with no offset suffix.
        assertThat(task.get("deadline").asString()).isEqualTo("2099-01-02T03:04:05");
        assertThat(task.get("created_at").asString()).doesNotContain("Z").doesNotContain("+");
        assertThat(task.get("status").asString()).isEqualTo("todo");

        assertThat(fieldNames(task.get("creator")))
                .containsExactlyInAnyOrder(
                        "id", "username", "avatar_url", "position", "member_status", "github_login", "is_placeholder");
    }

    @Test
    void authPayloadsUseSnakeCase() throws Exception {
        JsonNode user = register("creator@example.com", "creator", "password-123");

        assertThat(fieldNames(user))
                .containsExactlyInAnyOrder(
                        "id",
                        "username",
                        "avatar_url",
                        "position",
                        "member_status",
                        "github_login",
                        "is_placeholder",
                        "email",
                        "role",
                        "created_at");

        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                {"email": "creator@example.com", "password": "password-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(fieldNames(json(body))).containsExactlyInAnyOrder("access_token", "token_type");
        assertThat(json(body).get("token_type").asString()).isEqualTo("bearer");
    }

    @Test
    void progressAndSkillPayloadsUseSnakeCase() throws Exception {
        String auth = bearerFor("creator@example.com", "creator", "password-123");
        int userId = json(mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, auth))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asInt();

        String body = mockMvc.perform(get("/api/v1/users/" + userId + "/progress"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(fieldNames(json(body)))
                .containsExactlyInAnyOrder("user_id", "total_experience", "skills_count", "average_level", "skills");
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
