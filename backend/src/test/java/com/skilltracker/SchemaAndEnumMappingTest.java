package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskStatus;
import com.skilltracker.domain.User;
import com.skilltracker.repository.TaskRepository;
import com.skilltracker.repository.UserRepository;
import com.skilltracker.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the two mapping decisions that would silently corrupt the existing database: the Flyway
 * schema must satisfy Hibernate's validation, and task status must round-trip through the
 * {@code taskstatus} PostgreSQL enum using its lower snake_case labels.
 */
class SchemaAndEnumMappingTest extends IntegrationTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void taskStatusRoundTripsThroughThePostgresEnum() {
        User creator = userRepository.saveAndFlush(new User("creator@example.com", "creator", "hash", "user"));

        Task task = new Task("Round trip", creator.getId());
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.saveAndFlush(task);
        entityManager.clear();

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        Object stored = entityManager
                .createNativeQuery("select status::text from tasks where id = :id")
                .setParameter("id", task.getId())
                .getSingleResult();
        assertThat(stored).isEqualTo("in_progress");
    }

    @Test
    void baselineMigrationSeedsTheDefaultLabels() {
        Object count = entityManager
                .createNativeQuery("select count(*) from labels where name = 'Backend'")
                .getSingleResult();
        assertThat(((Number) count).intValue()).isEqualTo(1);
    }
}
