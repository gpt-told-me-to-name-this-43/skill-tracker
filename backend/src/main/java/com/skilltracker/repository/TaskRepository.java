package com.skilltracker.repository;

import com.skilltracker.domain.Task;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Integer>, JpaSpecificationExecutor<Task> {

    /**
     * Locks the task row so a status change and its XP award cannot interleave with a concurrent
     * completion of the same task.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") Integer id);

    Optional<Task> findByGithubIssueNumber(Integer githubIssueNumber);

    @Query("select task.id from Task task where task.id in :ids")
    List<Integer> findExistingIds(@Param("ids") Collection<Integer> ids);
}
