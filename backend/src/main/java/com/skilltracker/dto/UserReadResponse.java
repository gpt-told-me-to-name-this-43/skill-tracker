package com.skilltracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skilltracker.domain.MemberStatus;
import com.skilltracker.domain.User;
import java.time.LocalDateTime;

/** Self view returned by register and {@code /auth/me}: adds the email and role. */
public record UserReadResponse(
        Integer id,
        String username,
        String avatarUrl,
        String position,
        MemberStatus memberStatus,
        String githubLogin,
        @JsonProperty("is_placeholder") boolean placeholder,
        String email,
        String role,
        LocalDateTime createdAt) {

    public static UserReadResponse from(User user) {
        return new UserReadResponse(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getPosition(),
                user.getMemberStatus(),
                user.getGithubLogin(),
                user.isPlaceholder(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt());
    }
}
