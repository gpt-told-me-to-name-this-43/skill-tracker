package com.skilltracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record TaskCreateRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @Min(1) @Max(5) Integer difficulty,
        LocalDateTime deadline,
        Integer assigneeId) {

    public TaskCreateRequest {
        title = Text.trim(title);
        difficulty = difficulty == null ? 3 : difficulty;
    }
}
