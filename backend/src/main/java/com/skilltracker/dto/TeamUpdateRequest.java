package com.skilltracker.dto;

import com.skilltracker.json.Patch;
import jakarta.validation.constraints.AssertTrue;

public record TeamUpdateRequest(Patch<String> name, Patch<String> description) {

    public TeamUpdateRequest {
        name = name == null ? Patch.absent() : name;
        description = description == null ? Patch.absent() : description;

        if (name.isPresent() && name.value() != null) {
            name = Patch.of(name.value().trim());
        }
        if (description.isPresent()) {
            description = Patch.of(Text.trimToNull(description.value()));
        }
    }

    @AssertTrue(message = "Name must not be empty")
    public boolean isNameUsable() {
        String value = name.value();
        return value == null || (!value.isEmpty() && value.length() <= 100);
    }
}
