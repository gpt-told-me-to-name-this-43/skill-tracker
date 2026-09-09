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

/** The XP a task pays out for one skill when it is completed. */
@Entity
@Table(name = "task_skills")
public class TaskSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "task_id", nullable = false)
    private Integer taskId;

    @Column(name = "skill_id", nullable = false)
    private Integer skillId;

    @Column(name = "exp_reward", nullable = false)
    private int expReward;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", insertable = false, updatable = false)
    private Skill skill;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TaskSkill() {}

    public TaskSkill(Integer taskId, Integer skillId, int expReward) {
        this.taskId = taskId;
        this.skillId = skillId;
        this.expReward = expReward;
    }

    public Integer getId() {
        return id;
    }

    public Integer getTaskId() {
        return taskId;
    }

    public Integer getSkillId() {
        return skillId;
    }

    public int getExpReward() {
        return expReward;
    }

    public Skill getSkill() {
        return skill;
    }
}
