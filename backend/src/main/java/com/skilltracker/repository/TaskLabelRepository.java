package com.skilltracker.repository;

import com.skilltracker.domain.TaskLabel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, Integer> {

    @Query("""
            select taskLabel from TaskLabel taskLabel
            join fetch taskLabel.label
            where taskLabel.taskId in :taskIds
            order by taskLabel.id
            """)
    List<TaskLabel> findByTaskIdsWithLabel(@Param("taskIds") Collection<Integer> taskIds);

    List<TaskLabel> findByTaskIdOrderById(Integer taskId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("delete from TaskLabel taskLabel where taskLabel.taskId = :taskId")
    void deleteAllByTaskId(@Param("taskId") Integer taskId);
}
