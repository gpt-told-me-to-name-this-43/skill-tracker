package com.skilltracker.service;

import com.skilltracker.domain.ExperienceLog;
import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskSkill;
import com.skilltracker.domain.UserSkill;
import com.skilltracker.repository.ExperienceLogRepository;
import com.skilltracker.repository.TaskSkillRepository;
import com.skilltracker.repository.UserSkillRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays out the XP a task carries to its assignee.
 *
 * <p>Awarding is idempotent per {@code (task, user, skill)}: an existing {@link ExperienceLog} row
 * short-circuits the award, and the matching unique constraint is the final guard when two requests
 * race. Callers hold a row lock on the task, so a repeated completion cannot double-credit.
 */
@Component
public class ExperienceAwarder {

    private static final Logger log = LoggerFactory.getLogger(ExperienceAwarder.class);

    private final TaskSkillRepository taskSkillRepository;
    private final UserSkillRepository userSkillRepository;
    private final ExperienceLogRepository experienceLogRepository;

    public ExperienceAwarder(
            TaskSkillRepository taskSkillRepository,
            UserSkillRepository userSkillRepository,
            ExperienceLogRepository experienceLogRepository) {
        this.taskSkillRepository = taskSkillRepository;
        this.userSkillRepository = userSkillRepository;
        this.experienceLogRepository = experienceLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void awardForTask(Task task) {
        Integer assigneeId = task.getAssigneeId();
        if (assigneeId == null) {
            log.warn("Task {} is done without assignee; skipping XP award", task.getId());
            return;
        }

        List<TaskSkill> rewards = taskSkillRepository.findByTaskIdOrderById(task.getId());
        for (TaskSkill reward : rewards) {
            if (experienceLogRepository.existsByTaskIdAndUserIdAndSkillId(
                    task.getId(), assigneeId, reward.getSkillId())) {
                continue;
            }

            UserSkill userSkill = userSkillRepository
                    .findByUserIdAndSkillIdForUpdate(assigneeId, reward.getSkillId())
                    .orElseGet(
                            () -> userSkillRepository.saveAndFlush(new UserSkill(assigneeId, reward.getSkillId(), 0)));

            userSkill.addExperience(reward.getExpReward());
            userSkillRepository.save(userSkill);
            experienceLogRepository.saveAndFlush(
                    new ExperienceLog(assigneeId, reward.getSkillId(), task.getId(), reward.getExpReward()));
        }
    }
}
