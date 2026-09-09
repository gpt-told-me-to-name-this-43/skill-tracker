package com.skilltracker.dto;

import com.skilltracker.json.Patch;
import jakarta.validation.constraints.AssertTrue;

/**
 * A blank name is accepted by validation on purpose: the service reports it as a 409 conflict, which
 * is the behaviour the previous backend had.
 */
public record SkillUpdateRequest(Patch<String> name, Patch<String> description) {

    public SkillUpdateRequest {
        name = name == null ? Patch.absent() : name;
        description = description == null ? Patch.absent() : description;
    }

    @AssertTrue(message = "String should have at most 100 characters")
    public boolean isNameWithinLength() {
        String value = name.value();
        return value == null || (!value.isEmpty() && value.length() <= 100);
    }
}
