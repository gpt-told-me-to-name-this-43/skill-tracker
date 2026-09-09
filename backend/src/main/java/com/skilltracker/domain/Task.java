package com.skilltracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Convert(converter = TaskStatusConverter.class)
    @JdbcType(PostgresEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "taskstatus")
    private TaskStatus status = TaskStatus.TODO;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer difficulty = 3;

    private LocalDateTime deadline;

    @Column(name = "creator_id", nullable = false)
    private Integer creatorId;

    @Column(name = "assignee_id")
    private Integer assigneeId;

    @Column(name = "approved_by_id")
    private Integer approvedById;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "github_issue_number")
    private Integer githubIssueNumber;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Task() {}

    public Task(String title, Integer creatorId) {
        this.title = title;
        this.creatorId = creatorId;
    }

    public void clearApproval() {
        this.approvedById = null;
        this.approvedAt = null;
    }

    public void approve(Integer approverId, LocalDateTime approvedAt) {
        this.approvedById = approverId;
        this.approvedAt = approvedAt;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public Integer getCreatorId() {
        return creatorId;
    }

    public Integer getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Integer assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Integer getApprovedById() {
        return approvedById;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public Integer getGithubIssueNumber() {
        return githubIssueNumber;
    }

    public void setGithubIssueNumber(Integer githubIssueNumber) {
        this.githubIssueNumber = githubIssueNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
