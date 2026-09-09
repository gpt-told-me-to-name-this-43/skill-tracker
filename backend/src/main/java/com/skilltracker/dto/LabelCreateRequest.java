package com.skilltracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.regex.Pattern;

public record LabelCreateRequest(@NotBlank @Size(max = 80) String name, String color) {

    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public LabelCreateRequest {
        name = Text.trim(name);
        color = Text.trimToNull(color) == null ? null : color;
    }

    @AssertTrue(message = "Color must use #RRGGBB format")
    public boolean isColorValid() {
        return color == null || COLOR.matcher(color).matches();
    }
}
