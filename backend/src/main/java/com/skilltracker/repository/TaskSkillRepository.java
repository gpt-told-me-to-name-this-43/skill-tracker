package com.skilltracker.repository;

import com.skilltracker.domain.TaskSkill;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskSkillRepository extends JpaRepository<TaskSkill, Integer> {

    @Query("""
            select taskSkill from TaskSkill taskSkill
            join fetch taskSkill.skill
            where taskSkill.taskId = :taskId
            order by taskSkill.id
            """)
    List<TaskSkill> findByTaskIdWithSkill(@Param("taskId") Integer taskId);

    List<TaskSkill> findByTaskIdOrderById(Integer taskId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("delete from TaskSkill taskSkill where taskSkill.taskId = :taskId")
    void deleteAllByTaskId(@Param("taskId") Integer taskId);
}
