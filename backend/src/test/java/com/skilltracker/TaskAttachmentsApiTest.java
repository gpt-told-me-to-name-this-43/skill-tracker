package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.config.StorageProperties;
import com.skilltracker.support.ApiTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

class TaskAttachmentsApiTest extends ApiTest {

    @Autowired
    private StorageProperties storageProperties;

    private String creatorAuth;
    private String assigneeAuth;
    private int creatorId;
    private int taskId;
    private int otherTaskId;

    @BeforeEach
    void createFixtures() throws Exception {
        creatorId = register("creator@example.com", "creator", "password-123")
                .get("id")
                .asInt();
        creatorAuth = "Bearer " + login("creator@example.com", "password-123");
        assigneeAuth = bearerFor("developer@example.com", "developer", "password-123");

        taskId = createTask(creatorAuth, """
                {"title": "Add Kanban"}
                """).get("id").asInt();
        otherTaskId = createTask(creatorAuth, """
                {"title": "Implement labels"}
                """).get("id").asInt();
    }

    @Test
    void creatingAnAttachmentRecordsItsAuthorWithoutTheEmail() throws Exception {
        JsonNode attachment = addLink("Kanban mockup", "https://www.figma.com/example", creatorAuth);

        assertThat(attachment.get("name").asString()).isEqualTo("Kanban mockup");
        assertThat(attachment.get("url").asString()).isEqualTo("https://www.figma.com/example");
        assertThat(attachment.get("created_by").get("id").asInt()).isEqualTo(creatorId);
        assertThat(attachment.get("created_by").get("username").asString()).isEqualTo("creator");
        assertThat(attachment.get("created_by").has("email")).isFalse();
        assertThat(attachment.get("created_at").isNull()).isFalse();
    }

    @Test
    void attachmentsAreListedInCreationOrderAndCounted() throws Exception {
        addLink("First", "https://example.com/1", creatorAuth);
        addLink("Second", "https://example.com/2", assigneeAuth);

        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/attachments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[0].created_by.username").value("creator"))
                .andExpect(jsonPath("$[1].name").value("Second"))
                .andExpect(jsonPath("$[1].created_by.username").value("developer"));

        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(jsonPath("$.attachments_count").value(2))
                .andExpect(jsonPath("$.attachments.length()").value(2));
    }

    @Test
    void deletingAnAttachmentRemovesIt() throws Exception {
        int attachmentId =
                addLink("Doc", "https://example.com/doc", creatorAuth).get("id").asInt();

        mockMvc.perform(delete("/api/v1/tasks/" + taskId + "/attachments/" + attachmentId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/attachments"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingIsScopedToTheOwningTask() throws Exception {
        int attachmentId =
                addLink("Doc", "https://example.com/doc", creatorAuth).get("id").asInt();

        mockMvc.perform(delete("/api/v1/tasks/" + otherTaskId + "/attachments/" + attachmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Attachment not found"));
        mockMvc.perform(get("/api/v1/tasks/" + taskId + "/attachments"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void deletingAnUnknownAttachmentIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/" + taskId + "/attachments/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachingToAnUnknownTaskIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/999999/attachments")
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Doc", "url": "https://example.com/doc"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAnAttachmentRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/attachments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Doc", "url": "https://example.com/doc"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/file", "file:///etc/passwd", "javascript:alert(1)", "example.com", ""})
    void nonHttpUrlsAreRejected(String url) throws Exception {
        link("Doc", url, creatorAuth).andExpect(status().isUnprocessableEntity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/a", "https://example.com/a"})
    void httpAndHttpsAreAccepted(String url) throws Exception {
        link("Doc", url, creatorAuth).andExpect(status().isCreated());
    }

    @Test
    void aBlankAttachmentNameIsRejected() throws Exception {
        link("   ", "https://example.com/a", creatorAuth).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void uploadingAFileStoresItUnderTheUploadDirectory() throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/tasks/" + taskId + "/attachments/upload")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("report.pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String url = json(body).get("url").asString();
        assertThat(url).startsWith("/uploads/task-attachments/").endsWith("-report.pdf");

        Path stored = storageProperties.taskAttachmentsPath().resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(Files.readString(stored)).isEqualTo("hello");

        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    /** A traversal attempt must not escape the upload directory or the stored file name. */
    @Test
    void aTraversingFileNameIsReducedToASafeName() throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/tasks/" + taskId + "/attachments/upload")
                        .file(new MockMultipartFile("file", "../../../etc/passwd", "text/plain", "payload".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("passwd"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String url = json(body).get("url").asString();
        assertThat(url).doesNotContain("..");

        Path uploadRoot = storageProperties.uploadPath();
        try (Stream<Path> files = Files.walk(storageProperties.taskAttachmentsPath())) {
            assertThat(files.filter(Files::isRegularFile))
                    .allSatisfy(path -> assertThat(path.normalize()).startsWith(uploadRoot));
        }
    }

    @Test
    void anEmptyUploadIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/tasks/" + taskId + "/attachments/upload")
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Uploaded file is empty"));
    }

    @Test
    void anUploadWithoutTheFileFieldIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/tasks/" + taskId + "/attachments/upload")
                        .file(new MockMultipartFile("document", "a.txt", "text/plain", "x".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("File field is missing"));
    }

    @Test
    void anUploadThatIsNotMultipartIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/attachments/upload")
                        .header(HttpHeaders.AUTHORIZATION, creatorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Expected multipart form data"));
    }

    @Test
    void uploadingRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/tasks/" + taskId + "/attachments/upload")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode addLink(String name, String url, String authorization) throws Exception {
        return json(link(name, url, authorization)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private ResultActions link(String name, String url, String authorization) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks/" + taskId + "/attachments")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "url": "%s"}
                        """.formatted(name, url)));
    }
}
