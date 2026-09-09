package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.domain.User;
import com.skilltracker.repository.UserRepository;
import com.skilltracker.security.JwtService;
import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Users and tokens created by the previous Python backend must keep working after the migration.
 *
 * <p>The bcrypt digests below were produced outside this project by an independent implementation
 * in the same {@code $2b$}/cost-12 format passlib emits, and the tokens were signed with a plain
 * HMAC-SHA256 the way PyJWT does. Verifying them here proves the compatibility rather than merely
 * checking that Java agrees with itself.
 */
class LegacyCredentialCompatibilityTest extends ApiTest {

    /** Signed with the test JWT secret, {@code sub} of "1" and an {@code exp} in 2100. */
    private static final String PY_JWT_VALID =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ"
                    + ".VYA3fgUDxTy1nD4OWt8F9aJABPvz8h16vqH1K12PxVE";

    private static final String PY_JWT_EXPIRED =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZXhwIjoxNzM1Njg5NjAwfQ"
                    + ".m39ZAo3UxC2ZDXOs35in9cX97mq8as92HJXxbocHhww";

    private static final String PY_JWT_WITHOUT_SUBJECT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJleHAiOjQxMDI0NDQ4MDB9"
            + ".EziIZLKSQb7SCjmDDhkjumlNOaeKFNx9FwDFVywE0hY";

    private static final String PY_JWT_NON_NUMERIC_SUBJECT =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJub3QtYS1udW1iZXIiLCJleHAiOjQxMDI0NDQ4MDB9"
                    + ".YC1Os183LzYWbweHZSchYkFZJ6DWnDZwRx4-0-b0AW0";

    private static final String PASSLIB_HASH_OF_PASSWORD123 =
            "$2b$12$xaot7XOkD0d0IDrWZmVfxuAZt6zFPSchF8VKb6G0mssior.aGlfKm";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
        "password123, $2b$12$xaot7XOkD0d0IDrWZmVfxuAZt6zFPSchF8VKb6G0mssior.aGlfKm",
        "secret-123, $2b$12$cdoz/1lije6eoaqkWkyuz.n6/URUi7QaTeWMEal5psl5VGe.fi34q",
        "'correct horse battery staple', $2b$12$T5FMhY.8TBvySNn.8xR5ReTEklxfmIuaQX4w9uoJvhgcfFzGgpDg6"
    })
    void verifiesBcryptDigestsWrittenByThePythonBackend(String password, String hash) {
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
        assertThat(passwordEncoder.matches(password + "x", hash)).isFalse();
    }

    /** Canonical OpenBSD bcrypt vectors, the same implementation passlib wraps. */
    @ParameterizedTest
    @CsvSource({
        "abc, $2a$06$If6bvum7DFjUnE9p2uDeDu0YHzrHM6tf.iqN8.yx.jNN1ILEf7h0i",
        "a, $2a$06$m0CrhHm10qJ3lXRY.5zDGO3rS2KdeeWLuGmsfGlMfOxih58VYVfxe",
        "abcdefghijklmnopqrstuvwxyz, $2a$06$.rCVZVOThsIa97pEDOxvGuRRgzG64bvtJ0938xuqzv18d3ZpQhstC"
    })
    void verifiesCanonicalBcryptVectors(String password, String hash) {
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }

    @Test
    void anExistingUserCanLogInWithTheStoredPythonHash() throws Exception {
        userRepository.saveAndFlush(new User("legacy@example.com", "legacy", PASSLIB_HASH_OF_PASSWORD123, "user"));

        String token = login("legacy@example.com", "password123");

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("legacy@example.com"));
    }

    @Test
    void loggingInDoesNotRewriteTheStoredHash() throws Exception {
        userRepository.saveAndFlush(new User("legacy@example.com", "legacy", PASSLIB_HASH_OF_PASSWORD123, "user"));

        login("legacy@example.com", "password123");

        User reloaded = userRepository.findByEmail("legacy@example.com").orElseThrow();
        assertThat(reloaded.getHashedPassword()).isEqualTo(PASSLIB_HASH_OF_PASSWORD123);
    }

    @Test
    void acceptsATokenIssuedByThePythonBackend() throws Exception {
        User user = userRepository.saveAndFlush(
                new User("legacy@example.com", "legacy", PASSLIB_HASH_OF_PASSWORD123, "user"));
        assertThat(user.getId()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + PY_JWT_VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("legacy@example.com"));
    }

    @Test
    void rejectsExpiredAndMalformedLegacyTokens() throws Exception {
        userRepository.saveAndFlush(new User("legacy@example.com", "legacy", PASSLIB_HASH_OF_PASSWORD123, "user"));

        for (String token : new String[] {PY_JWT_EXPIRED, PY_JWT_WITHOUT_SUBJECT, PY_JWT_NON_NUMERIC_SUBJECT}) {
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.message").value("Invalid token"));
        }
    }

    @Test
    void issuedTokensCarryOnlyTheSubjectAndExpiryClaims() {
        String token = jwtService.createAccessToken(42);

        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        var claims = json(payload);

        assertThat(claims.get("sub").asString()).isEqualTo("42");
        assertThat(claims.has("exp")).isTrue();
        assertThat(claims.properties()).hasSize(2);
        assertThat(jwtService.parseSubject(token)).isEqualTo(42);
    }
}
