package com.skilltracker.service;

import com.skilltracker.domain.TaskStatus;
import com.skilltracker.dto.TaskLintWarningResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Quality heuristics for a task. Pure over plain values so the rules can be exercised without a
 * database; nothing here blocks a write.
 */
public final class TaskLintRules {

    public static final int TITLE_MIN_LENGTH = 8;
    public static final int TITLE_MIN_WORDS = 2;
    public static final int DESCRIPTION_MIN_LENGTH = 30;
    public static final Duration DEADLINE_SOON_WINDOW = Duration.ofHours(24);
    public static final int XP_MIN_PER_DIFFICULTY = 10;
    public static final int XP_MAX_PER_DIFFICULTY = 100;
    public static final int SUSPICIOUS_SINGLE_REWARD = 500;

    private static final String SEVERITY_INFO = "info";
    private static final String SEVERITY_WARNING = "warning";

    private TaskLintRules() {}

    public record Input(
            String title,
            String description,
            TaskStatus status,
            int difficulty,
            LocalDateTime deadline,
            Integer assigneeId,
            int labelCount,
            List<Integer> expRewards) {}

    public static List<TaskLintWarningResponse> lint(Input task, LocalDateTime now) {
        List<TaskLintWarningResponse> warnings = new ArrayList<>();

        appendTitleWarnings(task, warnings);
        appendDescriptionWarnings(task, warnings);

        if (task.expRewards().isEmpty()) {
            warnings.add(new TaskLintWarningResponse(
                    "no_skill_rewards",
                    "skills",
                    SEVERITY_WARNING,
                    "Task has no skill rewards; completing it will award no XP"));
        }

        if (task.status() != TaskStatus.DONE) {
            appendDeadlineWarnings(task, now, warnings);
        }

        if (task.status() == TaskStatus.IN_PROGRESS && task.assigneeId() == null) {
            warnings.add(new TaskLintWarningResponse(
                    "in_progress_without_assignee",
                    "assignee_id",
                    SEVERITY_WARNING,
                    "Task is in progress but has no assignee"));
        }

        appendRewardWarnings(task, warnings);

        if (task.labelCount() == 0) {
            warnings.add(new TaskLintWarningResponse("no_labels", "labels", SEVERITY_INFO, "Task has no labels"));
        }

        return warnings;
    }

    private static void appendTitleWarnings(Input task, List<TaskLintWarningResponse> warnings) {
        String title = task.title() == null ? "" : task.title().trim();
        if (title.length() < TITLE_MIN_LENGTH) {
            warnings.add(new TaskLintWarningResponse(
                    "title_too_short",
                    "title",
                    SEVERITY_WARNING,
                    "Title is shorter than " + TITLE_MIN_LENGTH + " characters"));
        } else if (wordCount(title) < TITLE_MIN_WORDS) {
            warnings.add(new TaskLintWarningResponse(
                    "title_not_descriptive",
                    "title",
                    SEVERITY_INFO,
                    "Title has fewer than " + TITLE_MIN_WORDS + " words"));
        }
    }

    private static void appendDescriptionWarnings(Input task, List<TaskLintWarningResponse> warnings) {
        String description =
                task.description() == null ? "" : task.description().trim();
        if (description.isEmpty()) {
            warnings.add(new TaskLintWarningResponse(
                    "description_missing", "description", SEVERITY_WARNING, "Task has no description"));
        } else if (description.length() < DESCRIPTION_MIN_LENGTH) {
            warnings.add(new TaskLintWarningResponse(
                    "description_too_short",
                    "description",
                    SEVERITY_INFO,
                    "Description is shorter than " + DESCRIPTION_MIN_LENGTH + " characters"));
        }
    }

    private static void appendDeadlineWarnings(Input task, LocalDateTime now, List<TaskLintWarningResponse> warnings) {
        if (task.deadline() == null) {
            warnings.add(new TaskLintWarningResponse("no_deadline", "deadline", SEVERITY_INFO, "Task has no deadline"));
            return;
        }
        if (task.deadline().isBefore(now)) {
            warnings.add(new TaskLintWarningResponse(
                    "deadline_past", "deadline", SEVERITY_WARNING, "Deadline is in the past"));
        } else if (task.deadline().isBefore(now.plus(DEADLINE_SOON_WINDOW))) {
            warnings.add(new TaskLintWarningResponse(
                    "deadline_soon", "deadline", SEVERITY_INFO, "Deadline is less than 24 hours away"));
        }
    }

    private static void appendRewardWarnings(Input task, List<TaskLintWarningResponse> warnings) {
        if (task.expRewards().isEmpty()) {
            return;
        }

        int total = task.expRewards().stream().mapToInt(Integer::intValue).sum();
        if (total < task.difficulty() * XP_MIN_PER_DIFFICULTY) {
            warnings.add(new TaskLintWarningResponse(
                    "xp_below_difficulty",
                    null,
                    SEVERITY_INFO,
                    "Total XP reward " + total + " looks low for difficulty " + task.difficulty()));
        } else if (total > task.difficulty() * XP_MAX_PER_DIFFICULTY) {
            warnings.add(new TaskLintWarningResponse(
                    "xp_above_difficulty",
                    null,
                    SEVERITY_WARNING,
                    "Total XP reward " + total + " looks high for difficulty " + task.difficulty()));
        }

        if (task.expRewards().stream().anyMatch(reward -> reward > SUSPICIOUS_SINGLE_REWARD)) {
            warnings.add(new TaskLintWarningResponse(
                    "suspicious_reward",
                    null,
                    SEVERITY_WARNING,
                    "A single skill reward exceeds " + SUSPICIOUS_SINGLE_REWARD + " XP"));
        }
    }

    private static int wordCount(String title) {
        return title.isEmpty() ? 0 : title.split("\\s+").length;
    }
}
