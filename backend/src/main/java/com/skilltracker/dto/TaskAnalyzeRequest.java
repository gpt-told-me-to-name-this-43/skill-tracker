package com.skilltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskAnalyzeRequest(@NotBlank @Size(max = 255) String title, String description) {

    public TaskAnalyzeRequest {
        title = Text.trim(title);
    }
}
