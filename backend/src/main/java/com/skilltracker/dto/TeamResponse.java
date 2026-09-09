package com.skilltracker.dto;

import com.skilltracker.domain.Team;
import java.time.LocalDateTime;
import java.util.List;

public record TeamResponse(
        Integer id,
        String name,
        String description,
        int memberCount,
        UserSummaryResponse lead,
        List<UserSummaryResponse> members,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TeamResponse of(Team team, UserSummaryResponse lead, List<UserSummaryResponse> members) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                members.size(),
                lead,
                members,
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
