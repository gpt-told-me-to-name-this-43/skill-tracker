package com.skilltracker.repository;

import com.skilltracker.domain.Skill;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, Integer> {

    @Query("select skill from Skill skill where lower(skill.name) = lower(:name)")
    Optional<Skill> findByNameIgnoreCase(@Param("name") String name);

    @Query("select skill from Skill skill order by skill.id")
    List<Skill> findAllOrderedById(Pageable pageable);
}
