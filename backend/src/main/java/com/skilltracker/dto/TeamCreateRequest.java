package com.skilltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(@NotBlank @Size(max = 100) String name, String description) {

    public TeamCreateRequest {
        name = Text.trim(name);
        description = Text.trimToNull(description);
    }
}
