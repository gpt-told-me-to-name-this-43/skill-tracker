package com.skilltracker.repository;

import com.skilltracker.domain.ExperienceLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperienceLogRepository extends JpaRepository<ExperienceLog, Integer> {

    boolean existsByTaskIdAndUserIdAndSkillId(Integer taskId, Integer userId, Integer skillId);

    @Query("""
            select log from ExperienceLog log
            where log.userId = :userId
            order by log.createdAt desc, log.id desc
            """)
    List<ExperienceLog> findByUserIdNewestFirst(@Param("userId") Integer userId, Pageable pageable);
}
