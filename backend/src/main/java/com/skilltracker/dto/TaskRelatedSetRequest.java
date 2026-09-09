package com.skilltracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record TaskRelatedSetRequest(@NotNull List<Integer> taskIds) {

    @AssertTrue(message = "Duplicate task ids are not allowed")
    public boolean isWithoutDuplicates() {
        return taskIds == null || Set.copyOf(taskIds).size() == taskIds.size();
    }
}
