package com.skilltracker.repository;

import com.skilltracker.domain.TaskRelation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRelationRepository extends JpaRepository<TaskRelation, Integer> {

    @Query("""
            select relation from TaskRelation relation
            join fetch relation.leftTask
            join fetch relation.rightTask
            where relation.leftTaskId in :taskIds or relation.rightTaskId in :taskIds
            order by relation.id
            """)
    List<TaskRelation> findInvolvingWithTasks(@Param("taskIds") Collection<Integer> taskIds);

    @Query("""
            select relation from TaskRelation relation
            where relation.leftTaskId in :taskIds or relation.rightTaskId in :taskIds
            order by relation.id
            """)
    List<TaskRelation> findInvolving(@Param("taskIds") Collection<Integer> taskIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from TaskRelation relation where relation.leftTaskId = :taskId or relation.rightTaskId = :taskId")
    void deleteInvolving(@Param("taskId") Integer taskId);
}
