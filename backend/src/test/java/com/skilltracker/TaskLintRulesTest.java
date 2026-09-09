package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.skilltracker.domain.TaskStatus;
import com.skilltracker.dto.TaskLintWarningResponse;
import com.skilltracker.service.TaskLintRules;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The lint heuristics are pure, so they are exercised directly over plain values. */
class TaskLintRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 13, 12, 0);

    private static TaskLintRules.Input clean() {
        return new TaskLintRules.Input(
                "Implement kanban board",
                "Add a kanban board with drag and drop support for tasks.",
                TaskStatus.TODO,
                3,
                NOW.plusDays(7),
                1,
                1,
                List.of(50, 50));
    }

    private static List<String> codes(TaskLintRules.Input input) {
        return TaskLintRules.lint(input, NOW).stream()
                .map(TaskLintWarningResponse::code)
                .toList();
    }

    @Test
    void aCleanTaskProducesNoWarnings() {
        assertThat(TaskLintRules.lint(clean(), NOW)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {
                "'Fix log', title_too_short",
                "'Fix logs', NULL",
                "'  Fix log  ', title_too_short",
                "Refactoring, title_not_descriptive",
                "'Fix login', NULL",
                "Fix, title_too_short"
            })
    void titleRules(String title, String expected) {
        assertThat(codes(withTitle(title))).isEqualTo(expected == null ? List.of() : List.of(expected));
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "NULL",
            value = {"NULL, description_missing", "'', description_missing", "'   ', description_missing"})
    void aMissingDescriptionWarns(String description, String expected) {
        assertThat(codes(withDescription(description))).isEqualTo(List.of(expected));
    }

    @Test
    void descriptionLengthRules() {
        assertThat(codes(withDescription("a".repeat(29)))).isEqualTo(List.of("description_too_short"));
        assertThat(codes(withDescription("a".repeat(30)))).isEmpty();
    }

    @Test
    void aTaskWithoutRewardsWarns() {
        assertThat(codes(withRewards(List.of()))).isEqualTo(List.of("no_skill_rewards"));
    }

    @Test
    void deadlineRules() {
        assertThat(codes(withDeadline(null))).isEqualTo(List.of("no_deadline"));
        assertThat(codes(withDeadline(NOW.minusMinutes(1)))).isEqualTo(List.of("deadline_past"));
        assertThat(codes(withDeadline(NOW.plusHours(23)))).isEqualTo(List.of("deadline_soon"));
        assertThat(codes(withDeadline(NOW.plusHours(25)))).isEmpty();
    }

    @Test
    void aFinishedTaskSkipsTheDeadlineRules() {
        TaskLintRules.Input input = clean();
        assertThat(codes(new TaskLintRules.Input(
                        input.title(),
                        input.description(),
                        TaskStatus.DONE,
                        input.difficulty(),
                        NOW.minusDays(1),
                        input.assigneeId(),
                        input.labelCount(),
                        input.expRewards())))
                .isEmpty();
    }

    @Test
    void inProgressWithoutAnAssigneeWarns() {
        assertThat(codes(withStatusAndAssignee(TaskStatus.IN_PROGRESS, null)))
                .isEqualTo(List.of("in_progress_without_assignee"));
        assertThat(codes(withStatusAndAssignee(TaskStatus.IN_PROGRESS, 1))).isEmpty();
        assertThat(codes(withStatusAndAssignee(TaskStatus.TODO, null))).isEmpty();
    }

    @Test
    void rewardsAreComparedAgainstTheDifficulty() {
        assertThat(codes(withRewards(List.of(29)))).isEqualTo(List.of("xp_below_difficulty"));
        assertThat(codes(withRewards(List.of(30)))).isEmpty();
        assertThat(codes(withRewards(List.of(300)))).isEmpty();
        assertThat(codes(withRewards(List.of(301)))).isEqualTo(List.of("xp_above_difficulty"));
        assertThat(codes(withRewards(List.of(250, 251)))).isEqualTo(List.of("xp_above_difficulty"));
        assertThat(codes(withRewards(List.of(500)))).isEqualTo(List.of("xp_above_difficulty"));
        assertThat(codes(withRewards(List.of(501)))).isEqualTo(List.of("xp_above_difficulty", "suspicious_reward"));
    }

    @Test
    void aTaskWithoutLabelsIsOnlyInformational() {
        List<TaskLintWarningResponse> warnings = TaskLintRules.lint(withLabelCount(0), NOW);

        assertThat(warnings).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo("no_labels");
            assertThat(warning.severity()).isEqualTo("info");
            assertThat(warning.field()).isEqualTo("labels");
        });
    }

    @Test
    void warningsCarryTheirSeverityAndField() {
        List<TaskLintWarningResponse> warnings = TaskLintRules.lint(
                new TaskLintRules.Input(
                        "Fix", clean().description(), TaskStatus.TODO, 3, NOW.plusDays(7), 1, 1, List.of()),
                NOW);

        assertThat(warnings)
                .filteredOn(warning -> warning.code().equals("title_too_short"))
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.severity()).isEqualTo("warning");
                    assertThat(warning.field()).isEqualTo("title");
                });
        assertThat(warnings)
                .filteredOn(warning -> warning.code().equals("no_skill_rewards"))
                .singleElement()
                .satisfies(warning -> assertThat(warning.field()).isEqualTo("skills"));
    }

    private static TaskLintRules.Input withTitle(String title) {
        TaskLintRules.Input base = clean();
        return new TaskLintRules.Input(
                title,
                base.description(),
                base.status(),
                base.difficulty(),
                base.deadline(),
                base.assigneeId(),
                base.labelCount(),
                base.expRewards());
    }

    private static TaskLintRules.Input withDescription(String description) {
        TaskLintRules.Input base = clean();
        return new TaskLintRules.Input(
                base.title(),
                description,
                base.status(),
                base.difficulty(),
                base.deadline(),
                base.assigneeId(),
                base.labelCount(),
                base.expRewards());
    }

    private static TaskLintRules.Input withDeadline(LocalDateTime deadline) {
        TaskLintRules.Input base = clean();
        return new TaskLintRules.Input(
                base.title(),
                base.description(),
                base.status(),
                base.difficulty(),
                deadline,
                base.assigneeId(),
                base.labelCount(),
                base.expRewards());
    }

    private static TaskLintRules.Input withRewards(List<Integer> rewards) {
        TaskLintRules.Input base = clean();
        return new TaskLintRules.Input(
                base.title(),
                base.description(),
                base.status(),
                base.difficulty(),
                base.deadline(),
                base.assigneeId(),
                base.labelCount(),
                rewards);
    }

    private static TaskLintRules.Input withLabelCount(int labelCount) {
        TaskLintRules.Input base = clean();
        return new TaskLintRules.Input(
                base.title(),
                base.description(),
                base.status(),
                base.difficulty(),
                base.deadline(),
                base.assigneeId(),
                labelCount,
                base.expRewards());
    }

    private static TaskLintRules.Input withStatusAndAssignee(TaskStatus status, Integer assigneeId) {
        TaskLintRules.Input base = clean();
        return new TaskLintRules.Input(
                base.title(),
                base.description(),
                status,
                base.difficulty(),
                base.deadline(),
                assigneeId,
                base.labelCount(),
                base.expRewards());
    }
}
