package com.skilltracker.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Unknown properties are rejected rather than ignored, matching the {@code extra="forbid"} pydantic
 * model this endpoint used. Every other request model keeps ignoring extras.
 */
public record SkillCreateRequest(
        @NotNull @Size(min = 1, max = 100) String name,
        String description,
        @JsonAnySetter Map<String, Object> unknownFields) {

    public SkillCreateRequest {
        unknownFields = unknownFields == null ? Map.of() : Map.copyOf(unknownFields);
    }

    @AssertTrue(message = "Extra inputs are not permitted")
    public boolean hasNoUnknownFields() {
        return unknownFields.isEmpty();
    }
}
