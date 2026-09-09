package com.skilltracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record TaskLabelsSetRequest(@NotNull List<Integer> labelIds) {

    @AssertTrue(message = "Duplicate label ids are not allowed")
    public boolean isWithoutDuplicates() {
        return labelIds == null || Set.copyOf(labelIds).size() == labelIds.size();
    }
}
