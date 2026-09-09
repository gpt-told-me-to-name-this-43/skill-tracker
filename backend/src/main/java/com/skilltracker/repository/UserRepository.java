package com.skilltracker.repository;

import com.skilltracker.domain.MemberStatus;
import com.skilltracker.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByGithubLogin(String githubLogin);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("""
            select user from User user
            where (:teamId is null or exists (
                      select membership from TeamMember membership
                      where membership.userId = user.id and membership.teamId = :teamId))
              and (:memberStatus is null or user.memberStatus = :memberStatus)
            order by user.id
            """)
    List<User> findFiltered(
            @Param("teamId") Integer teamId, @Param("memberStatus") MemberStatus memberStatus, Pageable pageable);

    @Query("select user from User user where user.id in :ids order by user.id")
    List<User> findAllByIdIn(@Param("ids") Collection<Integer> ids);
}
