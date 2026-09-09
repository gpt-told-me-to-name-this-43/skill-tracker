package com.skilltracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skilltracker.domain.MemberStatus;
import com.skilltracker.domain.User;

/** Public view of a user: everything except the email and the password hash. */
public record UserSummaryResponse(
        Integer id,
        String username,
        String avatarUrl,
        String position,
        MemberStatus memberStatus,
        String githubLogin,
        @JsonProperty("is_placeholder") boolean placeholder) {

    public static UserSummaryResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getPosition(),
                user.getMemberStatus(),
                user.getGithubLogin(),
                user.isPlaceholder());
    }
}
