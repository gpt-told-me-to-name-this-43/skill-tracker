package com.skilltracker.integration;

import java.util.List;

/** Unvalidated model output; {@code TaskSuggestionService} checks it against the database. */
public record RawTaskSuggestion(
        Integer difficulty, Integer estimatedDays, List<Integer> labelIds, List<SkillReward> skills) {

    public record SkillReward(int skillId, int expReward) {}

    public static RawTaskSuggestion empty() {
        return new RawTaskSuggestion(null, null, List.of(), List.of());
    }
}
