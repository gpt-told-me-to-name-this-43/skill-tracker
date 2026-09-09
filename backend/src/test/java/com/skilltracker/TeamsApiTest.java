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
import org.springframework.test.web.servlet.ResultActions;

class TeamsApiTest extends ApiTest {

    private String auth;
    private int ivanId;
    private int olgaId;

    @BeforeEach
    void createPeople() throws Exception {
        ivanId = register("ivan@example.com", "ivan", "password-123").get("id").asInt();
        olgaId = register("olga@example.com", "olga", "password-123").get("id").asInt();
        auth = "Bearer " + login("ivan@example.com", "password-123");
    }

    @Test
    void everyTeamEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/teams")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/teams/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Backend Team"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingATeamTrimsItsFields() throws Exception {
        createTeam("  Backend Team  ", "\"  Owns the API  \"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Backend Team"))
                .andExpect(jsonPath("$.description").value("Owns the API"))
                .andExpect(jsonPath("$.member_count").value(0))
                .andExpect(jsonPath("$.members.length()").value(0))
                .andExpect(jsonPath("$.lead").doesNotExist());
    }

    @Test
    void aDuplicateTeamNameConflictsCaseInsensitively() throws Exception {
        createTeam("Backend Team", "null").andExpect(status().isCreated());

        createTeam(" backend team ", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Team name already taken"));
    }

    @Test
    void aBlankTeamNameIsRejected() throws Exception {
        createTeam("   ", "null").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aMissingTeamIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/teams/999999").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Team not found"));
    }

    @Test
    void membersAndLeadAreSetTogether() throws Exception {
        int teamId = teamId(createTeam("Backend Team", "null"));

        setMembers(teamId, "[%d, %d]".formatted(ivanId, olgaId), String.valueOf(ivanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member_count").value(2))
                .andExpect(jsonPath("$.lead.id").value(ivanId))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].email").doesNotExist());
    }

    @Test
    void theLeadMustBeAMember() throws Exception {
        int teamId = teamId(createTeam("QA Team", "null"));

        setMembers(teamId, "[%d]".formatted(olgaId), String.valueOf(ivanId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Lead must be a team member"));
    }

    @Test
    void unknownMembersAreReported() throws Exception {
        int teamId = teamId(createTeam("QA Team", "null"));

        setMembers(teamId, "[999999, 999998]", "null")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Users not found: [999998, 999999]"));
    }

    @Test
    void duplicateMemberIdsAreRejected() throws Exception {
        int teamId = teamId(createTeam("QA Team", "null"));

        setMembers(teamId, "[%d, %d]".formatted(ivanId, ivanId), "null").andExpect(status().isUnprocessableEntity());
    }

    /** A user belongs to one team, so joining another one clears the previous membership and lead. */
    @Test
    void joiningAnotherTeamMovesTheUserAndClearsTheOldLead() throws Exception {
        int backendId = teamId(createTeam("Backend Team", "null"));
        int qaId = teamId(createTeam("QA Team", "null"));
        setMembers(backendId, "[%d]".formatted(ivanId), String.valueOf(ivanId)).andExpect(status().isOk());

        setMembers(qaId, "[%d, %d]".formatted(ivanId, olgaId), String.valueOf(olgaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member_count").value(2))
                .andExpect(jsonPath("$.lead.id").value(olgaId));

        mockMvc.perform(get("/api/v1/teams/" + backendId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.member_count").value(0))
                .andExpect(jsonPath("$.lead").doesNotExist());
    }

    @Test
    void removingEveryMemberEmptiesTheTeam() throws Exception {
        int teamId = teamId(createTeam("QA Team", "null"));
        setMembers(teamId, "[%d]".formatted(ivanId), String.valueOf(ivanId)).andExpect(status().isOk());

        setMembers(teamId, "[]", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member_count").value(0))
                .andExpect(jsonPath("$.lead").doesNotExist());
    }

    @Test
    void updatingATeamPatchesOnlyTheGivenFields() throws Exception {
        int teamId = teamId(createTeam("QA Team", "\"Quality\""));

        mockMvc.perform(patch("/api/v1/teams/" + teamId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Quality Team"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Quality Team"))
                .andExpect(jsonPath("$.description").value("Quality"));

        mockMvc.perform(patch("/api/v1/teams/" + teamId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void renamingOntoAnotherTeamConflicts() throws Exception {
        createTeam("Backend Team", "null").andExpect(status().isCreated());
        int qaId = teamId(createTeam("QA Team", "null"));

        mockMvc.perform(patch("/api/v1/teams/" + qaId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "backend team"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void teamsAreListedById() throws Exception {
        createTeam("Backend Team", "null").andExpect(status().isCreated());
        createTeam("QA Team", "null").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Backend Team"))
                .andExpect(jsonPath("$[1].name").value("QA Team"));
    }

    private ResultActions createTeam(String name, String description) throws Exception {
        return mockMvc.perform(post("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "description": %s}
                        """.formatted(name, description)));
    }

    private ResultActions setMembers(int teamId, String userIds, String leadId) throws Exception {
        return mockMvc.perform(put("/api/v1/teams/" + teamId + "/members")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"user_ids": %s, "lead_id": %s}
                        """.formatted(userIds, leadId)));
    }

    private int teamId(ResultActions result) throws Exception {
        return json(result.andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asInt();
    }
}
