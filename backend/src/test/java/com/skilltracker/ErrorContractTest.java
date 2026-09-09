package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Every failure has to arrive in the single {@code {"error": {...}}} envelope the client parses. */
class ErrorContractTest extends ApiTest {

    @Test
    void notFoundUsesTheErrorEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void anUnknownRouteIsAJsonNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Not Found"));
    }

    @Test
    void anUnsupportedMethodIsAJsonMethodNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/v1/labels"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.message").value("Method Not Allowed"));
    }

    @Test
    void unauthorizedCarriesTheBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.error.message").value("Not authenticated"));
    }

    @Test
    void aConflictUsesTheErrorEnvelope() throws Exception {
        createSkill("Docker");

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Docker"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Skill 'Docker' already exists"));
    }

    @Test
    void aValidationFailureCarriesTheFieldDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "username": "ab", "password": "short"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.message").value("Validation error"))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.error.details[0].loc").isArray())
                .andExpect(jsonPath("$.error.details[0].msg").isString());
    }

    @Test
    void aBadRequestUsesTheErrorEnvelope() throws Exception {
        String auth = bearerFor("creator@example.com", "creator", "password-123");

        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Late", "deadline": "2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Deadline must be in the future"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void aMalformedPathVariableIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/not-a-number/lint")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aMalformedTimestampIsAValidationError() throws Exception {
        String auth = bearerFor("creator@example.com", "creator", "password-123");

        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Bad deadline", "deadline": "not-a-date"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.message").value("Validation error"));
    }

    @Test
    void serviceUnavailableUsesTheErrorEnvelope() throws Exception {
        String auth = bearerFor("analyst@example.com", "analyst", "password-123");

        mockMvc.perform(post("/api/v1/tasks/analyze")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Fix login bug"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.message").isString());
    }

    @Test
    void theHealthEndpointIsPublicAndStable() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status": "ok"}
                        """));
    }
}
