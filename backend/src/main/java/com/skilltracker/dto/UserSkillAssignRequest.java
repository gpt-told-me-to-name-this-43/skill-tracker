package com.skilltracker.dto;

import jakarta.validation.constraints.NotNull;

public record UserSkillAssignRequest(@NotNull Integer skillId) {}
