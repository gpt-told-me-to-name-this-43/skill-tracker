package com.skilltracker.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Base class for HTTP contract tests driven through {@link MockMvc}. */
@AutoConfigureMockMvc
public abstract class ApiTest extends IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected JsonNode json(String content) {
        return objectMapper.readTree(content);
    }

    /** Registers a user and returns the created profile. */
    protected JsonNode register(String email, String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "username": "%s", "password": "%s"}
                                """.formatted(email, username, password)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(body);
    }

    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(body).get("access_token").asString();
    }

    /** Creates a task as the given caller and returns the created detail payload. */
    protected JsonNode createTask(String authorization, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tasks")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(response);
    }

    protected JsonNode createSkill(String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(response);
    }

    /** Registers a user, logs in, and returns a ready-to-use bearer header value. */
    protected String bearerFor(String email, String username, String password) throws Exception {
        register(email, username, password);
        return "Bearer " + login(email, password);
    }
}
