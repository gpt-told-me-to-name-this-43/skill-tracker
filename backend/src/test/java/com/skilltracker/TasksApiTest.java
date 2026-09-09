package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class TasksApiTest extends ApiTest {

    private String creatorAuth;
    private int creatorId;
    private int assigneeId;

    @BeforeEach
    void createPeople() throws Exception {
        JsonNode creator = register("creator@example.com", "creator", "password-123");
        creatorId = creator.get("id").asInt();
        creatorAuth = "Bearer " + login("creator@example.com", "password-123");
        assigneeId = register("developer@example.com", "developer", "password-123")
                .get("id")
                .asInt();
    }

    @Test
    void createSetsTheCreatorAndDefaults() throws Exception {
        JsonNode task = createTask(creatorAuth, """
                {"title": "New task"}
                """);

        org.assertj.core.api.Assertions.assertThat(task.get("creator_id").asInt())
                .isEqualTo(creatorId);
        org.assertj.core.api.Assertions.assertThat(task.get("status").asString())
                .isEqualTo("todo");
        org.assertj.core.api.Assertions.assertThat(task.get("difficulty").asInt())
                .isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(task.get("assignee_id").isNull())
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(task.get("github_url").isNull())
                .isTrue();
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New task"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDeadlineInThePastIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Late", "deadline": "2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Deadline must be in the future"));
    }

    @Test
    void anOffsetAwareDeadlineIsStoredAsNaiveUtc() throws Exception {
        JsonNode task = createTask(creatorAuth, """
                {"title": "Aware deadline", "deadline": "2099-03-01T15:30:00+05:00"}
                """);

        org.assertj.core.api.Assertions.assertThat(task.get("deadline").asString())
                .isEqualTo("2099-03-01T10:30:00");
    }

    @Test
    void difficultyOutsideOneToFiveIsAValidationError() throws Exception {
        for (String difficulty : new String[] {"0", "6"}) {
            mockMvc.perform(post("/api/v1/tasks")
                            .header(HttpHeaders.AUTHORIZATION, creatorAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title": "T", "difficulty": %s}
                                    """.formatted(difficulty)))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void patchIgnoresStatusAndAssignee() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Original"}
                """).get("id").asInt();

        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Renamed", "status": "done", "assignee_id": %d}
                                """.formatted(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.assignee_id").doesNotExist());
    }

    @Test
    void patchWithAnExplicitNullClearsTheDescription() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Has text", "description": "something"}
                """).get("id").asInt();

        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void patchWithoutADescriptionKeepsIt() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Has text", "description": "something"}
                """).get("id").asInt();

        mockMvc.perform(patch("/api/v1/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Renamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("something"));
    }

    @Test
    void doneIsGatedByReviewAndApproval() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Workflow"}
                """).get("id").asInt();

        changeStatus(taskId, "done").andExpect(status().isBadRequest());

        changeStatus(taskId, "in_progress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));
        changeStatus(taskId, "review")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("review"));

        changeStatus(taskId, "done")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Task must be approved before moving to done"));

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/approve").header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved_by_id").value(creatorId))
                .andExpect(jsonPath("$.approved_at").isNotEmpty());

        changeStatus(taskId, "done")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));
    }

    @Test
    void leavingReviewClearsTheApproval() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Workflow"}
                """).get("id").asInt();

        changeStatus(taskId, "review").andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/approve").header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isOk());

        changeStatus(taskId, "in_progress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved_by_id").doesNotExist())
                .andExpect(jsonPath("$.approved_at").doesNotExist());
    }

    @Test
    void reopeningAFinishedTaskClearsTheApproval() throws Exception {
        int taskId = approvedAndDoneTask();

        changeStatus(taskId, "in_progress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.approved_by_id").doesNotExist());
    }

    @Test
    void onlyTasksInReviewCanBeApproved() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Workflow"}
                """).get("id").asInt();

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/approve").header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Only tasks in review can be approved"));
    }

    @Test
    void anUnknownStatusIsAValidationError() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Workflow"}
                """).get("id").asInt();

        changeStatus(taskId, "invalid").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void changingTheStatusOfAMissingTaskIsNotFound() throws Exception {
        changeStatus(999999, "review")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Task with id 999999 not found"));
    }

    @Test
    void assignAndUnassign() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Assignable"}
                """).get("id").asInt();

        assign(taskId, String.valueOf(assigneeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee_id").value(assigneeId))
                .andExpect(jsonPath("$.assignee.username").value("developer"));

        assign(taskId, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee_id").doesNotExist());
    }

    @Test
    void assigningAMissingUserIsNotFound() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Assignable"}
                """).get("id").asInt();

        assign(taskId, "999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("User with id 999999 not found"));
    }

    @Test
    void listFiltersByStatusAssigneeAndDifficulty() throws Exception {
        int first = createTask(creatorAuth, """
                {"title": "First", "difficulty": 1}
                """).get("id").asInt();
        createTask(creatorAuth, """
                {"title": "Second", "difficulty": 5}
                """);

        changeStatus(first, "in_progress").andExpect(status().isOk());
        assign(first, String.valueOf(assigneeId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks?status=in_progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first));
        mockMvc.perform(get("/api/v1/tasks?status=done"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/tasks?assignee_id=" + assigneeId))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/tasks?difficulty=5"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/tasks?difficulty=9")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listIsNewestFirstAndReadableWithoutAToken() throws Exception {
        int first = createTask(creatorAuth, """
                {"title": "First"}
                """).get("id").asInt();
        int second = createTask(creatorAuth, """
                {"title": "Second"}
                """).get("id").asInt();

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[0].creator.username").value("creator"))
                .andExpect(jsonPath("$[0].creator.email").doesNotExist())
                .andExpect(jsonPath("$[0].attachments_count").value(0))
                .andExpect(jsonPath("$[0].related_tasks_count").value(0));
    }

    @Test
    void aMissingTaskIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Task with id 999999 not found"));
    }

    @Test
    void aBlankTitleIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    private int approvedAndDoneTask() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Workflow"}
                """).get("id").asInt();
        changeStatus(taskId, "review").andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/approve").header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isOk());
        changeStatus(taskId, "done").andExpect(status().isOk());
        return taskId;
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(int taskId, String status)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(status)));
    }

    private org.springframework.test.web.servlet.ResultActions assign(int taskId, String assigneeId) throws Exception {
        return mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assignee_id": %s}
                        """.formatted(assigneeId)));
    }
}
