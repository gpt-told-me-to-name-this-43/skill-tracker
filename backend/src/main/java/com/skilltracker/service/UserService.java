package com.skilltracker.service;

import com.skilltracker.domain.MemberStatus;
import com.skilltracker.domain.Team;
import com.skilltracker.domain.TeamMember;
import com.skilltracker.domain.User;
import com.skilltracker.dto.TeamBriefResponse;
import com.skilltracker.dto.UserPublicResponse;
import com.skilltracker.dto.WorkspaceProfileUpdateRequest;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.repository.OffsetLimit;
import com.skilltracker.repository.TeamMemberRepository;
import com.skilltracker.repository.TeamRepository;
import com.skilltracker.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    public UserService(
            UserRepository userRepository, TeamMemberRepository teamMemberRepository, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<UserPublicResponse> listUsers(int limit, int offset, Integer teamId, MemberStatus memberStatus) {
        List<User> users =
                userRepository.findFiltered(teamId, memberStatus, OffsetLimit.of(limit, offset, Sort.unsorted()));
        Map<Integer, TeamBriefResponse> teams = teamsOf(users);

        return users.stream()
                .map(user -> UserPublicResponse.of(user, teams.get(user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserPublicResponse getUser(Integer userId) {
        User user = requireUser(userId);
        return UserPublicResponse.of(user, teamsOf(List.of(user)).get(userId));
    }

    @Transactional
    public UserPublicResponse updateWorkspaceProfile(Integer userId, WorkspaceProfileUpdateRequest request) {
        User user = requireUser(userId);

        if (request.avatarUrl().isPresent()) {
            user.setAvatarUrl(request.avatarUrl().value());
        }
        if (request.position().isPresent()) {
            user.setPosition(request.position().value());
        }
        if (request.memberStatus().isPresent() && request.memberStatus().value() != null) {
            user.setMemberStatus(request.memberStatus().value());
        }

        User saved = userRepository.save(user);
        return UserPublicResponse.of(saved, teamsOf(List.of(saved)).get(userId));
    }

    /** Resolves the team of each given user in one query. */
    private Map<Integer, TeamBriefResponse> teamsOf(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }

        List<Integer> userIds = users.stream().map(User::getId).toList();
        List<TeamMember> memberships = teamMemberRepository.findByUserIdIn(userIds);
        if (memberships.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Team> teamsById = new HashMap<>();
        teamRepository
                .findAllById(memberships.stream().map(TeamMember::getTeamId).toList())
                .forEach(team -> teamsById.put(team.getId(), team));

        Map<Integer, TeamBriefResponse> teams = new HashMap<>();
        for (TeamMember membership : memberships) {
            Team team = teamsById.get(membership.getTeamId());
            if (team != null) {
                teams.put(membership.getUserId(), new TeamBriefResponse(team.getId(), team.getName()));
            }
        }
        return teams;
    }

    private User requireUser(Integer userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
