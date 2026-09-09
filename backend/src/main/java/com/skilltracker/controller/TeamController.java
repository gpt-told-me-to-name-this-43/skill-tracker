package com.skilltracker.controller;

import com.skilltracker.dto.TeamCreateRequest;
import com.skilltracker.dto.TeamMembersSetRequest;
import com.skilltracker.dto.TeamResponse;
import com.skilltracker.dto.TeamUpdateRequest;
import com.skilltracker.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<TeamResponse> listTeams() {
        return teamService.listTeams();
    }

    @GetMapping("/{teamId}")
    public TeamResponse getTeam(@PathVariable Integer teamId) {
        return teamService.getTeam(teamId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(@Valid @RequestBody TeamCreateRequest request) {
        // TODO(epic:auth-rbac): restrict team creation.
        return teamService.createTeam(request);
    }

    @PatchMapping("/{teamId}")
    public TeamResponse updateTeam(@PathVariable Integer teamId, @Valid @RequestBody TeamUpdateRequest request) {
        // TODO(epic:auth-rbac): restrict team updates.
        return teamService.updateTeam(teamId, request);
    }

    @PutMapping("/{teamId}/members")
    public TeamResponse setTeamMembers(
            @PathVariable Integer teamId, @Valid @RequestBody TeamMembersSetRequest request) {
        // TODO(epic:auth-rbac): restrict team membership updates.
        return teamService.setMembers(teamId, request);
    }
}
