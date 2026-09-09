package com.skilltracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskAttachmentCreateRequest(
        @NotBlank @Size(max = 200) String name, @NotNull String url) {

    public TaskAttachmentCreateRequest {
        name = Text.trim(name);
    }

    @AssertTrue(message = "URL scheme should be 'http' or 'https'")
    public boolean isUrlSupported() {
        return Text.isHttpUrl(url);
    }
}
