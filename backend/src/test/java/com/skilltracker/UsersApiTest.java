package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class UsersApiTest extends ApiTest {

    private String auth;
    private int activeId;
    private int awayId;

    @BeforeEach
    void createPeople() throws Exception {
        activeId = register("active@example.com", "active-user", "password-123")
                .get("id")
                .asInt();
        awayId = register("away@example.com", "away-user", "password-123")
                .get("id")
                .asInt();
        auth = "Bearer " + login("active@example.com", "password-123");

        mockMvc.perform(patch("/api/v1/users/" + awayId + "/workspace-profile")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"member_status": "away"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void listingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void listOmitsTheEmailAndFiltersByMemberStatus() throws Exception {
        mockMvc.perform(get("/api/v1/users?member_status=away").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("away-user"))
                .andExpect(jsonPath("$[0].member_status").value("away"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].team").doesNotExist());
    }

    @Test
    void listFiltersByTeam() throws Exception {
        String teamBody = mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "QA Team"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int teamId = json(teamBody).get("id").asInt();

        mockMvc.perform(put("/api/v1/teams/" + teamId + "/members")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user_ids": [%d], "lead_id": null}
                                """.formatted(awayId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users?team_id=" + teamId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("away-user"))
                .andExpect(jsonPath("$[0].team.id").value(teamId))
                .andExpect(jsonPath("$[0].team.name").value("QA Team"));

        mockMvc.perform(get("/api/v1/users?team_id=99999").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aMissingUserIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/999999").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("User not found"));
    }

    @Test
    void updatingTheWorkspaceProfileTrimsTheDisplayFields() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + activeId + "/workspace-profile")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatar_url": "  https://example.com/avatar.png  ",
                                 "position": "  QA Engineer  ",
                                 "member_status": "away"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.position").value("QA Engineer"))
                .andExpect(jsonPath("$.member_status").value("away"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void anExplicitNullMemberStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + activeId + "/workspace-profile")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"member_status": null}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void anExplicitNullPositionClearsIt() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + activeId + "/workspace-profile")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"position": "Engineer"}
                                """))
                .andExpect(jsonPath("$.position").value("Engineer"));

        mockMvc.perform(patch("/api/v1/users/" + activeId + "/workspace-profile")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"position": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").doesNotExist());
    }

    @Test
    void aNonHttpAvatarUrlIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + activeId + "/workspace-profile")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatar_url": "javascript:alert(1)"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void paginationBoundsAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/users?limit=0").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/users?limit=101").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v1/users?offset=-1").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnprocessableEntity());
    }
}
