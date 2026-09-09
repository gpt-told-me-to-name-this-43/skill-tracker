package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.domain.TaskStatus;
import com.skilltracker.domain.User;
import com.skilltracker.integration.GitHubIssue;
import com.skilltracker.integration.GitHubIssueSource;
import com.skilltracker.repository.TaskRepository;
import com.skilltracker.repository.UserRepository;
import com.skilltracker.support.ApiTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/** One-way GitHub import driven by a stubbed issue source. */
@Import(GitHubImportApiTest.StubIssueSourceConfiguration.class)
class GitHubImportApiTest extends ApiTest {

    @TestConfiguration
    static class StubIssueSourceConfiguration {
        @Bean
        @Primary
        StubIssueSource stubIssueSource() {
            return new StubIssueSource();
        }
    }

    static class StubIssueSource implements GitHubIssueSource {
        final List<GitHubIssue> issues = new ArrayList<>();

        @Override
        public List<GitHubIssue> fetchIssues() {
            return List.copyOf(issues);
        }
    }

    @Autowired
    private StubIssueSource source;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private String auth;
    private int importerId;

    @BeforeEach
    void createImporter() throws Exception {
        importerId = register("importer@example.com", "importer", "password-123")
                .get("id")
                .asInt();
        auth = "Bearer " + login("importer@example.com", "password-123");
        source.issues.clear();
    }

    private GitHubIssue issue(
            int number, String title, String body, String state, String assignee, List<String> labels) {
        return new GitHubIssue(number, title, body, state, assignee, labels);
    }

    @Test
    void openAndClosedIssuesBecomeTasks() throws Exception {
        source.issues.add(issue(1, "Open issue", "Open body", "open", null, List.of()));
        source.issues.add(issue(2, "Issue 2", "Body 2", "closed", null, List.of()));

        sync().andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.users_created").value(0));

        var open = taskRepository.findByGithubIssueNumber(1).orElseThrow();
        assertThat(open.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(open.getTitle()).isEqualTo("Open issue");
        assertThat(open.getDescription()).isEqualTo("Open body");
        assertThat(open.getCreatorId()).isEqualTo(importerId);
        assertThat(open.getDifficulty()).isEqualTo(3);
        assertThat(taskRepository.findByGithubIssueNumber(2).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.DONE);
    }

    /** A closed issue skips review and approval, and must not pay out any XP. */
    @Test
    void aClosedIssueAwardsNoExperienceAndSkipsApproval() throws Exception {
        source.issues.add(issue(1, "Issue 1", "Body", "closed", "octocat", List.of()));

        sync().andExpect(status().isOk());

        var task = taskRepository.findByGithubIssueNumber(1).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getApprovedAt()).isNull();
        assertThat(task.getApprovedById()).isNull();

        User ghost = userRepository.findByGithubLogin("octocat").orElseThrow();
        mockMvc.perform(get("/api/v1/users/" + ghost.getId() + "/experience-log"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aPlaceholderUserIsCreatedOnceAndReused() throws Exception {
        source.issues.add(issue(1, "Issue 1", "Body", "open", "octocat", List.of()));
        source.issues.add(issue(2, "Issue 2", "Body", "open", "octocat", List.of()));

        sync().andExpect(jsonPath("$.users_created").value(1));

        User ghost = userRepository.findByGithubLogin("octocat").orElseThrow();
        assertThat(ghost.isPlaceholder()).isTrue();
        assertThat(ghost.getUsername()).isEqualTo("octocat");
        assertThat(ghost.getEmail()).isEqualTo("octocat@users.noreply.github.com");

        sync().andExpect(jsonPath("$.users_created").value(0));
        assertThat(userRepository.findByGithubLogin("octocat")).isPresent();
    }

    @Test
    void anExistingLinkedUserIsNotDuplicated() throws Exception {
        User linked = userRepository.saveAndFlush(new User("dev@example.com", "real-dev", "hash", "user"));
        linked.setGithubLogin("octocat");
        userRepository.saveAndFlush(linked);

        source.issues.add(issue(1, "Issue 1", "Body", "open", "octocat", List.of()));

        sync().andExpect(jsonPath("$.users_created").value(0));

        assertThat(taskRepository.findByGithubIssueNumber(1).orElseThrow().getAssigneeId())
                .isEqualTo(linked.getId());
    }

    @Test
    void aUsernameCollisionGetsASuffix() throws Exception {
        userRepository.saveAndFlush(new User("taken@example.com", "octocat", "hash", "user"));
        source.issues.add(issue(1, "Issue 1", "Body", "open", "octocat", List.of()));

        sync().andExpect(jsonPath("$.users_created").value(1));

        User ghost = userRepository.findByGithubLogin("octocat").orElseThrow();
        assertThat(ghost.getUsername()).isEqualTo("octocat-2");
        assertThat(ghost.getEmail()).isEqualTo("octocat-2@users.noreply.github.com");
    }

    @Test
    void syncingTwiceWithoutRemoteChangesIsANoOp() throws Exception {
        source.issues.add(issue(1, "Issue 1", "Body 1", "open", null, List.of("bug")));
        source.issues.add(issue(2, "Issue 2", "Body 2", "closed", "octocat", List.of()));

        sync().andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.users_created").value(1));
        sync().andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.users_created").value(0));

        assertThat(taskRepository.count()).isEqualTo(2);
    }

    @Test
    void closingAnIssueBetweenSyncsFinishesTheTaskWithoutExperience() throws Exception {
        source.issues.add(issue(1, "Issue 1", "Body", "open", null, List.of()));
        sync().andExpect(jsonPath("$.created").value(1));

        source.issues.set(0, issue(1, "Issue 1", "Body", "closed", null, List.of()));
        sync().andExpect(jsonPath("$.updated").value(1));

        var task = taskRepository.findByGithubIssueNumber(1).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getApprovedAt()).isNull();
    }

    /** GitHub can only close: reopening upstream must not demote a finished task. */
    @Test
    void reopeningAnIssueKeepsTheTaskDone() throws Exception {
        source.issues.add(issue(1, "Issue 1", "Body", "closed", null, List.of()));
        sync().andExpect(jsonPath("$.created").value(1));

        source.issues.set(0, issue(1, "Issue 1", "Body", "open", null, List.of()));
        sync().andExpect(jsonPath("$.updated").value(0));

        assertThat(taskRepository.findByGithubIssueNumber(1).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.DONE);
    }

    @Test
    void changedFieldsAreCopiedAcross() throws Exception {
        source.issues.add(issue(1, "Old", "Old body", "open", null, List.of()));
        sync().andExpect(jsonPath("$.created").value(1));

        source.issues.set(0, issue(1, "New", "New body", "open", "octocat", List.of()));
        sync().andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.users_created").value(1));

        var task = taskRepository.findByGithubIssueNumber(1).orElseThrow();
        assertThat(task.getTitle()).isEqualTo("New");
        assertThat(task.getDescription()).isEqualTo("New body");
        assertThat(task.getAssigneeId()).isNotNull();
    }

    @Test
    void labelsAreMatchedCaseInsensitively() throws Exception {
        source.issues.add(issue(1, "Issue 1", "Body", "open", null, List.of("backend", "bug")));

        sync().andExpect(status().isOk());

        JsonNode labels = json(
                mockMvc.perform(get("/api/v1/labels")).andReturn().getResponse().getContentAsString());
        List<String> names = new ArrayList<>();
        labels.forEach(label -> names.add(label.get("name").asString()));
        assertThat(names).contains("Backend", "Bug").doesNotContain("backend", "bug");

        int taskId = taskRepository.findByGithubIssueNumber(1).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(jsonPath("$.labels.length()").value(2));
    }

    @Test
    void longTitlesAreTruncatedToTheColumnWidth() throws Exception {
        source.issues.add(issue(1, "x".repeat(250), "Body", "open", null, List.of()));

        sync().andExpect(status().isOk());

        assertThat(taskRepository.findByGithubIssueNumber(1).orElseThrow().getTitle())
                .isEqualTo("x".repeat(200));
    }

    @Test
    void theGithubUrlIsExposedOnImportedTasks() throws Exception {
        source.issues.add(issue(42, "Issue 42", "Body", "open", null, List.of()));
        sync().andExpect(status().isOk());

        int taskId = taskRepository.findByGithubIssueNumber(42).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/tasks/" + taskId))
                .andExpect(jsonPath("$.github_issue_number").value(42))
                .andExpect(jsonPath("$.github_url").value("https://github.com/acme/widgets/issues/42"));
    }

    @Test
    void syncingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/github/sync")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions sync() throws Exception {
        return mockMvc.perform(post("/api/v1/integrations/github/sync").header(HttpHeaders.AUTHORIZATION, auth));
    }
}
