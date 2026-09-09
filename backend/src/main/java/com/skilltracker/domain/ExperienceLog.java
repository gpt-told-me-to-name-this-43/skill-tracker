package com.skilltracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

/**
 * Append-only record of an XP award. The unique {@code (task_id, user_id, skill_id)} constraint is
 * what makes awarding idempotent even under concurrent completions.
 */
@Entity
@Table(name = "experience_logs")
public class ExperienceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "skill_id", nullable = false)
    private Integer skillId;

    @Column(name = "task_id", nullable = false)
    private Integer taskId;

    @Column(nullable = false)
    private int amount;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ExperienceLog() {}

    public ExperienceLog(Integer userId, Integer skillId, Integer taskId, int amount) {
        this.userId = userId;
        this.skillId = skillId;
        this.taskId = taskId;
        this.amount = amount;
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getSkillId() {
        return skillId;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public int getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
