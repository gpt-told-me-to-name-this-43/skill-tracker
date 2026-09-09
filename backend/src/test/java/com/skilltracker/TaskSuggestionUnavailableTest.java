package com.skilltracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Without an OpenRouter key the suggestion endpoint reports the dependency as unavailable. */
class TaskSuggestionUnavailableTest extends ApiTest {

    @Test
    void analyzingWithoutAConfiguredProviderIsServiceUnavailable() throws Exception {
        String auth = bearerFor("analyst@example.com", "analyst", "password-123");

        mockMvc.perform(post("/api/v1/tasks/analyze")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Fix login bug"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.message").value("ML suggestions are unavailable: set OPENROUTER_API_KEY"));
    }
}
