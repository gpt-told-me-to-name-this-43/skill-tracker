package com.skilltracker.repository;

import com.skilltracker.domain.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Integer> {

    List<Team> findAllByOrderByIdAsc();

    @Query("select team from Team team where lower(team.name) = lower(:name)")
    Optional<Team> findByNameIgnoreCase(@Param("name") String name);
}
