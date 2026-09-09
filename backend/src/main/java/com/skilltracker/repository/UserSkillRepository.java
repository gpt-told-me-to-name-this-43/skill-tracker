package com.skilltracker.repository;

import com.skilltracker.domain.UserSkill;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, Integer> {

    @Query("""
            select userSkill from UserSkill userSkill
            join fetch userSkill.skill
            where userSkill.userId = :userId
            order by userSkill.id
            """)
    List<UserSkill> findByUserIdWithSkill(@Param("userId") Integer userId);

    Optional<UserSkill> findByUserIdAndSkillId(Integer userId, Integer skillId);

    /** Serialises concurrent XP awards for the same (user, skill) pair. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select userSkill from UserSkill userSkill where userSkill.userId = :userId and userSkill.skillId = :skillId")
    Optional<UserSkill> findByUserIdAndSkillIdForUpdate(
            @Param("userId") Integer userId, @Param("skillId") Integer skillId);
}
