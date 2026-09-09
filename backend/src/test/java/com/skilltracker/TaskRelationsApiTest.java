package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class TaskRelationsApiTest extends ApiTest {

    private String auth;
    private int first;
    private int second;
    private int third;

    @BeforeEach
    void createTasks() throws Exception {
        auth = bearerFor("creator@example.com", "creator", "password-123");
        first = createTask(auth, """
                {"title": "Add Kanban"}
                """).get("id").asInt();
        second = createTask(auth, """
                {"title": "Implement labels"}
                """).get("id").asInt();
        third = createTask(auth, """
                {"title": "Write docs"}
                """).get("id").asInt();
    }

    @Test
    void relationsAreVisibleFromBothSides() throws Exception {
        setRelated(first, "[%d]".formatted(second)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + second + "/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].title").value("Add Kanban"))
                .andExpect(jsonPath("$[0].status").value("todo"));

        mockMvc.perform(get("/api/v1/tasks/" + second))
                .andExpect(jsonPath("$.related_tasks.length()").value(1))
                .andExpect(jsonPath("$.related_tasks[0].id").value(first))
                .andExpect(jsonPath("$.related_tasks_count").value(1));
    }

    @Test
    void settingRelationsReplacesTheWholeSet() throws Exception {
        setRelated(first, "[%d, %d]".formatted(second, third))
                .andExpect(jsonPath("$.length()").value(2));

        setRelated(first, "[%d]".formatted(third))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(third));
    }

    @Test
    void clearingOneTaskLeavesOtherPairsIntact() throws Exception {
        setRelated(first, "[%d]".formatted(second)).andExpect(status().isOk());
        setRelated(second, "[%d]".formatted(third)).andExpect(status().isOk());

        setRelated(first, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/tasks/" + second + "/related"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(third));
    }

    @Test
    void settingTheSamePairFromBothSidesDoesNotDuplicateIt() throws Exception {
        setRelated(first, "[%d]".formatted(second)).andExpect(status().isOk());
        setRelated(second, "[%d]".formatted(first)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/" + first + "/related"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/tasks/" + second + "/related"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aTaskCannotRelateToItself() throws Exception {
        setRelated(first, "[%d]".formatted(first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Task cannot be related to itself"));
    }

    @Test
    void anUnknownRelatedTaskIsNotFoundAndNothingIsStored() throws Exception {
        setRelated(first, "[%d, 999999]".formatted(second))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("One or more related tasks were not found"));

        mockMvc.perform(get("/api/v1/tasks/" + first + "/related"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateTaskIdsAreRejected() throws Exception {
        setRelated(first, "[%d, %d]".formatted(second, second)).andExpect(status().isUnprocessableEntity());
    }

    private ResultActions setRelated(int taskId, String taskIds) throws Exception {
        return mockMvc.perform(put("/api/v1/tasks/" + taskId + "/related")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"task_ids": %s}
                        """.formatted(taskIds)));
    }
}
