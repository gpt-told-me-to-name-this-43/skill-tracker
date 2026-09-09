package com.skilltracker.dto;

import com.skilltracker.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(@NotNull TaskStatus status) {}
