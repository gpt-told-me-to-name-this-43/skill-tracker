package com.skilltracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record TeamMembersSetRequest(@NotNull List<Integer> userIds, Integer leadId) {

    @AssertTrue(message = "Duplicate user ids are not allowed")
    public boolean isWithoutDuplicates() {
        return userIds == null || Set.copyOf(userIds).size() == userIds.size();
    }
}
