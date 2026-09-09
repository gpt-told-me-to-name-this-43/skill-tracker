package com.skilltracker.dto;

import com.skilltracker.domain.MemberStatus;
import com.skilltracker.json.Patch;
import jakarta.validation.constraints.AssertTrue;

public record WorkspaceProfileUpdateRequest(
        Patch<String> avatarUrl, Patch<String> position, Patch<MemberStatus> memberStatus) {

    public WorkspaceProfileUpdateRequest {
        avatarUrl = avatarUrl == null ? Patch.absent() : avatarUrl;
        position = position == null ? Patch.absent() : position;
        memberStatus = memberStatus == null ? Patch.absent() : memberStatus;

        if (avatarUrl.isPresent()) {
            avatarUrl = Patch.of(Text.trimToNull(avatarUrl.value()));
        }
        if (position.isPresent()) {
            position = Patch.of(Text.trimToNull(position.value()));
        }
    }

    @AssertTrue(message = "URL scheme should be 'http' or 'https'")
    public boolean isAvatarUrlValid() {
        String value = avatarUrl.value();
        return value == null || (value.length() <= 2048 && Text.isHttpUrl(value));
    }

    @AssertTrue(message = "String should have at most 100 characters")
    public boolean isPositionWithinLength() {
        String value = position.value();
        return value == null || value.length() <= 100;
    }

    @AssertTrue(message = "member_status must not be null")
    public boolean isMemberStatusUsable() {
        return !memberStatus.isPresent() || memberStatus.value() != null;
    }
}
