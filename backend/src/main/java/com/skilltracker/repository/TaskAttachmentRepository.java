package com.skilltracker.repository;

import com.skilltracker.domain.TaskAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Integer> {

    @Query("""
            select attachment from TaskAttachment attachment
            join fetch attachment.createdBy
            where attachment.taskId in :taskIds
            order by attachment.id
            """)
    List<TaskAttachment> findByTaskIdsWithAuthor(@Param("taskIds") Collection<Integer> taskIds);

    @Query("""
            select attachment from TaskAttachment attachment
            join fetch attachment.createdBy
            where attachment.id = :id
            """)
    Optional<TaskAttachment> findByIdWithAuthor(@Param("id") Integer id);

    Optional<TaskAttachment> findByTaskIdAndId(Integer taskId, Integer id);

    @Query("""
            select attachment.taskId as taskId, count(attachment) as total from TaskAttachment attachment
            where attachment.taskId in :taskIds
            group by attachment.taskId
            """)
    List<TaskIdCount> countByTaskIds(@Param("taskIds") Collection<Integer> taskIds);
}
