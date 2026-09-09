package com.skilltracker.controller;

import com.skilltracker.domain.MemberStatus;
import com.skilltracker.dto.UserPublicResponse;
import com.skilltracker.dto.WorkspaceProfileUpdateRequest;
import com.skilltracker.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserPublicResponse> listUsers(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(name = "team_id", required = false) Integer teamId,
            @RequestParam(name = "member_status", required = false) MemberStatus memberStatus) {
        return userService.listUsers(limit, offset, teamId, memberStatus);
    }

    @GetMapping("/{userId}")
    public UserPublicResponse getUser(@PathVariable Integer userId) {
        return userService.getUser(userId);
    }

    @PatchMapping("/{userId}/workspace-profile")
    public UserPublicResponse updateWorkspaceProfile(
            @PathVariable Integer userId, @Valid @RequestBody WorkspaceProfileUpdateRequest request) {
        // TODO(epic:auth-rbac): restrict workspace profile updates.
        return userService.updateWorkspaceProfile(userId, request);
    }
}
