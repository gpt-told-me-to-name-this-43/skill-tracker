package com.skilltracker.repository;

import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional filters for the task list.
 *
 * <p>Expressed as specifications rather than a single query with {@code :param is null} guards:
 * PostgreSQL cannot infer the type of a bare parameter compared against null, which the native
 * {@code taskstatus} enum in particular trips over.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {}

    public static Specification<Task> matching(TaskStatus status, Integer assigneeId, Integer difficulty) {
        Specification<Task> specification = Specification.unrestricted();
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (assigneeId != null) {
            specification =
                    specification.and((root, query, builder) -> builder.equal(root.get("assigneeId"), assigneeId));
        }
        if (difficulty != null) {
            specification =
                    specification.and((root, query, builder) -> builder.equal(root.get("difficulty"), difficulty));
        }
        return specification;
    }
}
