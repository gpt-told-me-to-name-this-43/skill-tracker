package com.skilltracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;

public record TaskSkillsSetRequest(@NotNull @Valid List<Item> skills) {

    public record Item(
            @NotNull Integer skillId,
            @NotNull @Min(1) @Max(1000) Integer expReward) {}

    @AssertTrue(message = "skill_id values must be unique")
    public boolean isWithoutDuplicateSkills() {
        if (skills == null) {
            return true;
        }
        return skills.stream().map(Item::skillId).collect(Collectors.toSet()).size() == skills.size();
    }
}
