package com.skilltracker.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties the schema between tests so every case starts from the state a freshly migrated database
 * has, including the labels the baseline migration seeds.
 */
@TestComponent
public class DatabaseCleaner {

    private static final List<String> TABLES = List.of(
            "experience_logs",
            "task_skills",
            "task_labels",
            "task_attachments",
            "task_relations",
            "tasks",
            "user_skills",
            "team_members",
            "teams",
            "labels",
            "skills",
            "users");

    private static final List<String> BASELINE_LABELS =
            List.of("Backend", "Frontend", "DevOps", "Design", "QA", "Documentation", "Bug", "Feature", "Enhancement");

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void clean() {
        entityManager
                .createNativeQuery("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE")
                .executeUpdate();

        for (String label : BASELINE_LABELS) {
            entityManager
                    .createNativeQuery("INSERT INTO labels (name, color) VALUES (:name, NULL)")
                    .setParameter("name", label)
                    .executeUpdate();
        }
    }
}
