package com.skilltracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

/**
 * An unordered pair of related tasks. The smaller id is always stored on the left so a pair can only
 * exist once, which the {@code ck_task_relation_order} check constraint enforces.
 */
@Entity
@Table(name = "task_relations")
public class TaskRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "left_task_id", nullable = false)
    private Integer leftTaskId;

    @Column(name = "right_task_id", nullable = false)
    private Integer rightTaskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "left_task_id", insertable = false, updatable = false)
    private Task leftTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "right_task_id", insertable = false, updatable = false)
    private Task rightTask;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TaskRelation() {}

    public static TaskRelation between(Integer taskId, Integer relatedTaskId) {
        return new TaskRelation(Math.min(taskId, relatedTaskId), Math.max(taskId, relatedTaskId));
    }

    private TaskRelation(Integer leftTaskId, Integer rightTaskId) {
        this.leftTaskId = leftTaskId;
        this.rightTaskId = rightTaskId;
    }

    public Task otherSideOf(Integer taskId) {
        return leftTaskId.equals(taskId) ? rightTask : leftTask;
    }

    public Integer getId() {
        return id;
    }

    public Integer getLeftTaskId() {
        return leftTaskId;
    }

    public Integer getRightTaskId() {
        return rightTaskId;
    }
}
