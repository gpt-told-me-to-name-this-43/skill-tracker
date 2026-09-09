package com.skilltracker.service;

import com.skilltracker.domain.Team;
import com.skilltracker.domain.TeamMember;
import com.skilltracker.domain.User;
import com.skilltracker.dto.TeamResponse;
import com.skilltracker.dto.UserSummaryResponse;
import com.skilltracker.repository.TeamMemberRepository;
import com.skilltracker.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds team payloads, batching the membership and user lookups the response needs. */
@Component
public class TeamMapper {

    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public TeamMapper(TeamMemberRepository teamMemberRepository, UserRepository userRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    public TeamResponse toResponse(Team team) {
        return toResponses(List.of(team)).getFirst();
    }

    public List<TeamResponse> toResponses(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }

        List<Integer> teamIds = teams.stream().map(Team::getId).toList();
        Map<Integer, List<Integer>> memberIdsByTeam = new LinkedHashMap<>();
        Set<Integer> userIds = new LinkedHashSet<>();

        for (TeamMember membership : teamMemberRepository.findByTeamIdInOrderById(teamIds)) {
            memberIdsByTeam
                    .computeIfAbsent(membership.getTeamId(), key -> new ArrayList<>())
                    .add(membership.getUserId());
            userIds.add(membership.getUserId());
        }
        teams.forEach(team -> {
            if (team.getLeadId() != null) {
                userIds.add(team.getLeadId());
            }
        });

        Map<Integer, UserSummaryResponse> people = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllByIdIn(userIds)) {
                people.put(user.getId(), UserSummaryResponse.from(user));
            }
        }

        return teams.stream()
                .map(team -> TeamResponse.of(
                        team,
                        team.getLeadId() == null ? null : people.get(team.getLeadId()),
                        memberIdsByTeam.getOrDefault(team.getId(), List.of()).stream()
                                .map(people::get)
                                .toList()))
                .toList();
    }
}
