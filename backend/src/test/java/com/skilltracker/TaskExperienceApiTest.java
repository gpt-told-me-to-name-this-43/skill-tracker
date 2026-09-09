package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** The XP loop: rewards, awarding on completion, and the guards against paying out twice. */
class TaskExperienceApiTest extends ApiTest {

    private String creatorAuth;
    private int creatorId;
    private int assigneeId;
    private int skillId;
    private int otherSkillId;

    @BeforeEach
    void createFixtures() throws Exception {
        creatorId = register("creator@example.com", "creator", "password-123")
                .get("id")
                .asInt();
        creatorAuth = "Bearer " + login("creator@example.com", "password-123");
        assigneeId = register("developer@example.com", "developer", "password-123")
                .get("id")
                .asInt();
        skillId = createSkill("backend").get("id").asInt();
        otherSkillId = createSkill("frontend").get("id").asInt();
    }

    @Test
    void completingATaskAwardsExperienceOnce() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 150}]
                """.formatted(skillId));

        completeTask(taskId);

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].experience").value(150))
                .andExpect(jsonPath("$[0].level").value(2))
                .andExpect(jsonPath("$[0].current_level_xp").value(100))
                .andExpect(jsonPath("$[0].next_level_xp").value(200))
                .andExpect(jsonPath("$[0].progress_to_next_level").value(50));

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(150))
                .andExpect(jsonPath("$[0].task_id").value(taskId))
                .andExpect(jsonPath("$[0].skill_id").value(skillId));
    }

    @Test
    void repeatingTheDoneTransitionDoesNotDoubleTheAward() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 150}]
                """.formatted(skillId));
        completeTask(taskId);

        changeStatus(taskId, "done").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(jsonPath("$[0].experience").value(150));
        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reopeningAndFinishingAgainDoesNotAwardTwice() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 60}]
                """.formatted(skillId));
        completeTask(taskId);

        changeStatus(taskId, "in_progress").andExpect(status().isOk());
        changeStatus(taskId, "review").andExpect(status().isOk());
        approve(taskId).andExpect(status().isOk());
        changeStatus(taskId, "done").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(jsonPath("$[0].experience").value(60));
        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reassigningAfterCompletionDoesNotMoveTheExperience() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 40}]
                """.formatted(skillId));
        completeTask(taskId);

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignee_id": %d}
                                """.formatted(creatorId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(jsonPath("$[0].experience").value(40));
        mockMvc.perform(get("/api/v1/users/" + creatorId + "/skills"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/users/" + creatorId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aTaskCompletedWithoutAnAssigneeAwardsNothing() throws Exception {
        int taskId = createTask(creatorAuth, """
                {"title": "Unassigned"}
                """).get("id").asInt();
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 40}]
                """.formatted(skillId));

        completeTask(taskId);

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rewardsAttachedToAFinishedTaskArePaidImmediately() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        completeTask(taskId);

        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 50}]
                """.formatted(skillId));

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(jsonPath("$[0].experience").value(50));
    }

    @Test
    void replacingRewardsOnAFinishedTaskOnlyPaysTheNewSkill() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        completeTask(taskId);

        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 50}]
                """.formatted(skillId));
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 50}, {"skill_id": %d, "exp_reward": 30}]
                """.formatted(skillId, otherSkillId));

        String body = mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var skills = json(body);
        assertThat(skills.size()).isEqualTo(2);
        assertThat(skills.get(0).get("experience").asInt()).isEqualTo(50);
        assertThat(skills.get(1).get("experience").asInt()).isEqualTo(30);

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void changingTheRewardOfAnAlreadyPaidSkillDoesNotPayAgain() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        completeTask(taskId);
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 50}]
                """.formatted(skillId));

        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 500}]
                """.formatted(skillId));

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/skills"))
                .andExpect(jsonPath("$[0].experience").value(50));
        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rewardsOnAnUnfinishedTaskAwardNothing() throws Exception {
        int taskId = taskAssignedTo(assigneeId);

        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 50}]
                """.formatted(skillId));

        mockMvc.perform(get("/api/v1/users/" + assigneeId + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rewardsCanBeReadBackAndCleared() throws Exception {
        int taskId = taskAssignedTo(assigneeId);
        setSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 50}]
                """.formatted(skillId));

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].exp_reward").value(50))
                .andExpect(jsonPath("$[0].skill.name").value("backend"));

        setSkills(taskId, "[]");
        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/skills"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnknownSkillIsNotFoundAndDuplicatesAreRejected() throws Exception {
        int taskId = taskAssignedTo(assigneeId);

        putSkills(taskId, """
                [{"skill_id": 999999, "exp_reward": 50}]
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Skill 999999 not found"));

        putSkills(taskId, """
                        [{"skill_id": %d, "exp_reward": 50}, {"skill_id": %d, "exp_reward": 30}]
                        """.formatted(skillId, skillId)).andExpect(status().isUnprocessableEntity());

        putSkills(taskId, """
                [{"skill_id": %d, "exp_reward": 0}]
                """.formatted(skillId)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rewardsOfAMissingTaskAreNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/999999/skills")).andExpect(status().isNotFound());
    }

    @Test
    void theExperienceLogOfAMissingUserIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999/experience-log"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("User 999999 not found"));
    }

    private int taskAssignedTo(int userId) throws Exception {
        int taskId = createTask(creatorAuth, """
                        {"title": "Deliver the feature", "assignee_id": %d}
                        """.formatted(userId)).get("id").asInt();
        return taskId;
    }

    private void completeTask(int taskId) throws Exception {
        changeStatus(taskId, "review").andExpect(status().isOk());
        approve(taskId).andExpect(status().isOk());
        changeStatus(taskId, "done").andExpect(status().isOk());
    }

    private void setSkills(int taskId, String skills) throws Exception {
        putSkills(taskId, skills).andExpect(status().isOk());
    }

    private ResultActions putSkills(int taskId, String skills) throws Exception {
        return mockMvc.perform(put("/api/v1/tasks/" + taskId + "/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"skills": %s}
                        """.formatted(skills)));
    }

    private ResultActions changeStatus(int taskId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(status)));
    }

    private ResultActions approve(int taskId) throws Exception {
        return mockMvc.perform(
                patch("/api/v1/tasks/" + taskId + "/approve").header(HttpHeaders.AUTHORIZATION, creatorAuth));
    }
}
