package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SkillsApiTest extends ApiTest {

    @Test
    void createAndFetch() throws Exception {
        int skillId = createSkillWithDescription("Python", "Backend");

        mockMvc.perform(get("/api/v1/skills/" + skillId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Python"))
                .andExpect(jsonPath("$.description").value("Backend"));

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aMissingSkillIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/skills/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Skill 999999 not found"));
    }

    @Test
    void aDuplicateNameConflictsCaseInsensitively() throws Exception {
        createSkill("Docker");

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "docker"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownPropertiesAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Docker", "unexpected": 1}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anExplicitNullClearsTheDescription() throws Exception {
        int skillId = createSkillWithDescription("Go", "Backend");

        mockMvc.perform(patch("/api/v1/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());

        mockMvc.perform(get("/api/v1/skills/" + skillId))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void omittingTheDescriptionKeepsIt() throws Exception {
        int skillId = createSkillWithDescription("Go", "Backend");

        mockMvc.perform(patch("/api/v1/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Golang"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Golang"))
                .andExpect(jsonPath("$.description").value("Backend"));
    }

    @Test
    void aWhitespaceOnlyNameConflicts() throws Exception {
        int skillId = createSkillId("Rust");

        mockMvc.perform(patch("/api/v1/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   "}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/skills/" + skillId))
                .andExpect(jsonPath("$.name").value("Rust"));
    }

    @Test
    void renamingOntoAnotherSkillConflicts() throws Exception {
        createSkill("Python");
        int dockerId = createSkillId("Docker");

        mockMvc.perform(patch("/api/v1/skills/" + dockerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "python"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/skills/" + dockerId))
                .andExpect(jsonPath("$.name").value("Docker"));
    }

    @Test
    void changingOnlyTheCaseOfItsOwnNameIsAllowed() throws Exception {
        int skillId = createSkillId("docker");

        mockMvc.perform(patch("/api/v1/skills/" + skillId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Docker"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Docker"));
    }

    @Test
    void deleteRemovesTheSkill() throws Exception {
        int skillId = createSkillId("Temporary");

        mockMvc.perform(delete("/api/v1/skills/" + skillId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/skills/" + skillId)).andExpect(status().isNotFound());
    }

    @Test
    void assigningASkillTracksProgress() throws Exception {
        int userId =
                register("dev@example.com", "dev", "password-123").get("id").asInt();
        int skillId = createSkillId("Python");

        mockMvc.perform(post("/api/v1/users/" + userId + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skill_id": %d}
                                """.formatted(skillId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.experience").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.current_level_xp").value(0))
                .andExpect(jsonPath("$.next_level_xp").value(100))
                .andExpect(jsonPath("$.progress_to_next_level").value(0))
                .andExpect(jsonPath("$.skill.name").value("Python"));

        mockMvc.perform(post("/api/v1/users/" + userId + "/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skill_id": %d}
                                """.formatted(skillId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/users/" + userId + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(userId))
                .andExpect(jsonPath("$.total_experience").value(0))
                .andExpect(jsonPath("$.skills_count").value(1))
                .andExpect(jsonPath("$.average_level").value(1.0));
    }

    @Test
    void skillsOfAMissingUserAreNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999/skills"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("User 999999 not found"));
    }

    private int createSkillId(String name) throws Exception {
        return createSkill(name).get("id").asInt();
    }

    private int createSkillWithDescription(String name, String description) throws Exception {
        String body = mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "description": "%s"}
                                """.formatted(name, description)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(body).get("id").asInt();
    }
}
