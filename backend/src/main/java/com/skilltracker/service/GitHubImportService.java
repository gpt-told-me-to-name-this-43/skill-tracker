package com.skilltracker.service;

import com.skilltracker.domain.Label;
import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskLabel;
import com.skilltracker.domain.TaskStatus;
import com.skilltracker.domain.User;
import com.skilltracker.dto.GitHubSyncResponse;
import com.skilltracker.integration.GitHubIssue;
import com.skilltracker.integration.GitHubIssueSource;
import com.skilltracker.repository.LabelRepository;
import com.skilltracker.repository.TaskLabelRepository;
import com.skilltracker.repository.TaskRepository;
import com.skilltracker.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-way synchronisation of GitHub issues into tasks.
 *
 * <p>A deliberate departure from the normal workflow (see docs/decisions.md): an issue closed on
 * GitHub moves its task straight to {@code done} without review, approval or an XP award, and
 * reopening an issue never demotes the task.
 */
@Service
public class GitHubImportService {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final String NOREPLY_DOMAIN = "@users.noreply.github.com";

    private final GitHubIssueSource source;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public GitHubImportService(
            GitHubIssueSource source,
            TaskRepository taskRepository,
            UserRepository userRepository,
            LabelRepository labelRepository,
            TaskLabelRepository taskLabelRepository,
            PasswordEncoder passwordEncoder) {
        this.source = source;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.labelRepository = labelRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public GitHubSyncResponse sync(Integer currentUserId) {
        List<GitHubIssue> issues = source.fetchIssues();

        int created = 0;
        int updated = 0;
        int usersCreated = 0;
        Map<String, Integer> assigneeCache = new HashMap<>();
        Map<String, Integer> labelCache = new HashMap<>();

        for (GitHubIssue issue : issues) {
            Integer assigneeId = null;
            if (issue.assigneeLogin() != null) {
                ResolvedUser resolved = resolveAssignee(issue.assigneeLogin(), assigneeCache);
                assigneeId = resolved.userId();
                if (resolved.wasCreated()) {
                    usersCreated++;
                }
            }

            List<Integer> labelIds = issue.labels().stream()
                    .map(name -> resolveLabel(name, labelCache))
                    .toList();

            Optional<Task> existing = taskRepository.findByGithubIssueNumber(issue.number());
            if (existing.isEmpty()) {
                createTask(issue, assigneeId, labelIds, currentUserId);
                created++;
            } else if (updateTask(existing.get(), issue, assigneeId, labelIds)) {
                updated++;
            }
        }

        return new GitHubSyncResponse(created, updated, usersCreated);
    }

    private void createTask(GitHubIssue issue, Integer assigneeId, List<Integer> labelIds, Integer creatorId) {
        Task task = new Task(truncateTitle(issue.title()), creatorId);
        task.setDescription(issue.body());
        task.setStatus(mapStatus(issue.state()));
        task.setAssigneeId(assigneeId);
        task.setGithubIssueNumber(issue.number());
        taskRepository.saveAndFlush(task);

        if (!labelIds.isEmpty()) {
            replaceLabels(task.getId(), labelIds);
        }
    }

    private boolean updateTask(Task task, GitHubIssue issue, Integer assigneeId, List<Integer> labelIds) {
        boolean changed = false;

        String title = truncateTitle(issue.title());
        if (!title.equals(task.getTitle())) {
            task.setTitle(title);
            changed = true;
        }
        if (!java.util.Objects.equals(task.getDescription(), issue.body())) {
            task.setDescription(issue.body());
            changed = true;
        }
        // GitHub can only close a task here: reopening an issue never demotes a finished task.
        if ("closed".equals(issue.state()) && task.getStatus() != TaskStatus.DONE) {
            task.setStatus(TaskStatus.DONE);
            changed = true;
        }
        // An assignee cleared on GitHub is left alone so local assignments are not wiped.
        if (assigneeId != null && !assigneeId.equals(task.getAssigneeId())) {
            task.setAssigneeId(assigneeId);
            changed = true;
        }

        if (changed) {
            taskRepository.saveAndFlush(task);
        }

        Set<Integer> currentLabelIds = taskLabelRepository.findByTaskIdOrderById(task.getId()).stream()
                .map(TaskLabel::getLabelId)
                .collect(Collectors.toSet());
        if (!currentLabelIds.equals(new HashSet<>(labelIds))) {
            replaceLabels(task.getId(), labelIds);
            changed = true;
        }

        return changed;
    }

    private void replaceLabels(Integer taskId, List<Integer> labelIds) {
        taskLabelRepository.deleteAllByTaskId(taskId);
        labelIds.forEach(labelId -> taskLabelRepository.save(new TaskLabel(taskId, labelId)));
        taskLabelRepository.flush();
    }

    private record ResolvedUser(Integer userId, boolean wasCreated) {}

    private ResolvedUser resolveAssignee(String login, Map<String, Integer> cache) {
        Integer cached = cache.get(login);
        if (cached != null) {
            return new ResolvedUser(cached, false);
        }

        Optional<User> existing = userRepository.findByGithubLogin(login);
        if (existing.isPresent()) {
            cache.put(login, existing.get().getId());
            return new ResolvedUser(existing.get().getId(), false);
        }

        String username = login;
        int suffix = 1;
        while (isIdentityTaken(username)) {
            suffix++;
            username = login + "-" + suffix;
        }

        User placeholder = new User(
                // The real GitHub noreply convention; signing in with such a profile is impossible.
                username + NOREPLY_DOMAIN, username, passwordEncoder.encode(randomSecret()), "user");
        placeholder.setGithubLogin(login);
        placeholder.setPlaceholder(true);
        userRepository.saveAndFlush(placeholder);

        cache.put(login, placeholder.getId());
        return new ResolvedUser(placeholder.getId(), true);
    }

    private boolean isIdentityTaken(String username) {
        return userRepository.existsByUsername(username) || userRepository.existsByEmail(username + NOREPLY_DOMAIN);
    }

    private Integer resolveLabel(String name, Map<String, Integer> cache) {
        String key = name.toLowerCase(Locale.ROOT);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Label label = labelRepository
                .findByNameIgnoreCase(name)
                .orElseGet(() -> labelRepository.saveAndFlush(new Label(name, null)));
        cache.put(key, label.getId());
        return label.getId();
    }

    private TaskStatus mapStatus(String state) {
        return "closed".equals(state) ? TaskStatus.DONE : TaskStatus.TODO;
    }

    private String truncateTitle(String title) {
        return title.length() > TITLE_MAX_LENGTH ? title.substring(0, TITLE_MAX_LENGTH) : title;
    }

    private String randomSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
