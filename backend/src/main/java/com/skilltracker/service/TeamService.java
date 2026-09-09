package com.skilltracker.service;

import com.skilltracker.domain.Team;
import com.skilltracker.domain.TeamMember;
import com.skilltracker.dto.TeamCreateRequest;
import com.skilltracker.dto.TeamMembersSetRequest;
import com.skilltracker.dto.TeamResponse;
import com.skilltracker.dto.TeamUpdateRequest;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.exception.ConflictException;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.repository.TeamMemberRepository;
import com.skilltracker.repository.TeamRepository;
import com.skilltracker.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final TeamMapper teamMapper;
    private final ReadBack readBack;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            TeamMapper teamMapper,
            ReadBack readBack) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.teamMapper = teamMapper;
        this.readBack = readBack;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams() {
        return teamMapper.toResponses(teamRepository.findAllByOrderByIdAsc());
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(Integer teamId) {
        return teamMapper.toResponse(requireTeam(teamId));
    }

    @Transactional
    public TeamResponse createTeam(TeamCreateRequest request) {
        ensureNameAvailable(request.name(), null);

        Team team = teamRepository.save(new Team(request.name(), request.description()));
        readBack.flushAndDetach();
        return teamMapper.toResponse(requireTeam(team.getId()));
    }

    @Transactional
    public TeamResponse updateTeam(Integer teamId, TeamUpdateRequest request) {
        Team team = requireTeam(teamId);

        if (request.name().isPresent() && request.name().value() != null) {
            ensureNameAvailable(request.name().value(), team.getId());
            team.setName(request.name().value());
        }
        if (request.description().isPresent()) {
            team.setDescription(request.description().value());
        }

        teamRepository.save(team);
        readBack.flushAndDetach();
        return teamMapper.toResponse(requireTeam(teamId));
    }

    /**
     * Replaces the membership list wholesale. A user belongs to at most one team, so anyone joining
     * from another team is removed there first, and that team loses its lead if it was that user.
     */
    @Transactional
    public TeamResponse setMembers(Integer teamId, TeamMembersSetRequest request) {
        Team team = requireTeam(teamId);
        List<Integer> userIds = request.userIds();

        if (request.leadId() != null && !userIds.contains(request.leadId())) {
            throw new BadRequestException("Lead must be a team member");
        }
        requireUsersExist(userIds);

        Set<Integer> desired = new HashSet<>(userIds);
        List<TeamMember> currentMemberships = teamMemberRepository.findByTeamIdOrderById(teamId);
        Set<Integer> currentUserIds =
                currentMemberships.stream().map(TeamMember::getUserId).collect(Collectors.toSet());

        for (TeamMember membership : currentMemberships) {
            if (!desired.contains(membership.getUserId())) {
                teamMemberRepository.delete(membership);
            }
        }
        readBack.flushAndDetach();

        Map<Integer, TeamMember> incoming = new HashMap<>();
        if (!userIds.isEmpty()) {
            teamMemberRepository
                    .findByUserIdIn(userIds)
                    .forEach(membership -> incoming.put(membership.getUserId(), membership));
        }

        for (Integer userId : userIds) {
            TeamMember membership = incoming.get(userId);
            if (membership != null && membership.getTeamId().equals(teamId)) {
                continue;
            }
            if (membership != null) {
                moveUserFromPreviousTeam(membership);
            }
            if (!currentUserIds.contains(userId) || membership != null) {
                teamMemberRepository.save(new TeamMember(teamId, userId));
            }
        }

        Team reloaded = requireTeam(teamId);
        reloaded.setLeadId(request.leadId());
        teamRepository.save(reloaded);
        readBack.flushAndDetach();

        return teamMapper.toResponse(requireTeam(teamId));
    }

    private void moveUserFromPreviousTeam(TeamMember membership) {
        Team previousTeam = requireTeam(membership.getTeamId());
        if (membership.getUserId().equals(previousTeam.getLeadId())) {
            previousTeam.setLeadId(null);
            teamRepository.save(previousTeam);
        }
        teamMemberRepository.delete(membership);
        readBack.flushAndDetach();
    }

    private void requireUsersExist(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return;
        }

        Set<Integer> found = userRepository.findAllByIdIn(userIds).stream()
                .map(user -> user.getId())
                .collect(Collectors.toSet());
        Set<Integer> missing = new TreeSet<>(userIds);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new NotFoundException("Users not found: " + new ArrayList<>(missing));
        }
    }

    private void ensureNameAvailable(String name, Integer currentTeamId) {
        teamRepository
                .findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentTeamId))
                .ifPresent(existing -> {
                    throw new ConflictException("Team name already taken");
                });
    }

    private Team requireTeam(Integer teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> new NotFoundException("Team not found"));
    }
}
