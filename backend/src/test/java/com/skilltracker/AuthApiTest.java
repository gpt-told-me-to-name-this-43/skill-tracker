package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthApiTest extends ApiTest {

    private static final String EMAIL = "dev@example.com";
    private static final String USERNAME = "developer";
    private static final String PASSWORD = "secret-123";

    private static final String REGISTER_PAYLOAD = """
            {"email": "dev@example.com", "username": "developer", "password": "secret-123"}
            """;

    @Test
    void registerLoginAndMeRoundTrip() throws Exception {
        var created = register(EMAIL, USERNAME, PASSWORD);
        assertThat(created.has("hashed_password")).isFalse();
        assertThat(created.get("role").asString()).isEqualTo("user");

        String token = login(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.member_status").value("active"))
                .andExpect(jsonPath("$.is_placeholder").value(false));
    }

    @Test
    void meWithoutTokenIsUnauthorizedAndChallengesWithBearer() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.error.message").value("Not authenticated"));
    }

    @Test
    void meWithInvalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid token"));
    }

    @Test
    void registerNormalisesTheEmailAndDefaultsToTheUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Dev@Example.COM", "username": "developer", "password": "secret-123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("dev@example.com"))
                .andExpect(jsonPath("$.role").value("user"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        register(EMAIL, USERNAME, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "DEV@EXAMPLE.COM", "username": "another-user", "password": "secret-123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Email already registered"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        register(EMAIL, USERNAME, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "other@example.com", "username": "developer", "password": "secret-123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Username already taken"));
    }

    @Test
    void loginWithTheWrongPasswordIsUnauthorized() throws Exception {
        register(EMAIL, USERNAME, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dev@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid credentials"));
    }

    /**
     * bcrypt only reads the first 72 bytes, so a longer password must be rejected instead of being
     * silently truncated into an equivalent of its prefix.
     */
    @Test
    void passwordLongerThanSeventyTwoBytesIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dev@example.com", "username": "developer", "password": "%s"}
                                """.formatted("A".repeat(80))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.message").value("Validation error"));
    }

    @Test
    void passwordOfExactlySeventyTwoBytesIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dev@example.com", "username": "developer", "password": "%s"}
                                """.formatted("A".repeat(72))))
                .andExpect(status().isCreated());
    }

    @Test
    void aBodyThatIsNotJsonIsAValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.message").value("Validation error"));
    }

    @Test
    void registerRejectsAShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dev@example.com", "username": "developer", "password": "short"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void openApiDocumentDeclaresHttpBearerAuthentication() throws Exception {
        mockMvc.perform(get("/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(
                        jsonPath("$.components.securitySchemes.HTTPBearer.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.HTTPBearer.scheme")
                        .value("bearer"));
    }

    @Test
    void registeredUserIsUsableImmediately() throws Exception {
        assertThat(REGISTER_PAYLOAD).contains(EMAIL);
        String authorization = bearerFor(EMAIL, USERNAME, PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
    }
}
