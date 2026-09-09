package com.skilltracker.repository;

import com.skilltracker.domain.TeamMember;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Integer> {

    List<TeamMember> findByTeamIdOrderById(Integer teamId);

    List<TeamMember> findByTeamIdInOrderById(Collection<Integer> teamIds);

    List<TeamMember> findByUserIdIn(Collection<Integer> userIds);
}
