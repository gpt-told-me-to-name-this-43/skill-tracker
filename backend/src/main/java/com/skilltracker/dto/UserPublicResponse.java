package com.skilltracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skilltracker.domain.MemberStatus;
import com.skilltracker.domain.User;
import java.time.LocalDateTime;

/** Directory view of a user: role and team, but never the email. */
public record UserPublicResponse(
        Integer id,
        String username,
        String avatarUrl,
        String position,
        MemberStatus memberStatus,
        String githubLogin,
        @JsonProperty("is_placeholder") boolean placeholder,
        String role,
        TeamBriefResponse team,
        LocalDateTime createdAt) {

    public static UserPublicResponse of(User user, TeamBriefResponse team) {
        return new UserPublicResponse(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getPosition(),
                user.getMemberStatus(),
                user.getGithubLogin(),
                user.isPlaceholder(),
                user.getRole(),
                team,
                user.getCreatedAt());
    }
}
