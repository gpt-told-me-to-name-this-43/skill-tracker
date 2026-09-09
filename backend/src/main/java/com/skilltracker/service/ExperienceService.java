package com.skilltracker.service;

import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskSkill;
import com.skilltracker.domain.TaskStatus;
import com.skilltracker.dto.ExperienceLogResponse;
import com.skilltracker.dto.TaskSkillResponse;
import com.skilltracker.dto.TaskSkillsSetRequest;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.repository.ExperienceLogRepository;
import com.skilltracker.repository.OffsetLimit;
import com.skilltracker.repository.SkillRepository;
import com.skilltracker.repository.TaskRepository;
import com.skilltracker.repository.TaskSkillRepository;
import com.skilltracker.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperienceService {

    private final TaskSkillRepository taskSkillRepository;
    private final ExperienceLogRepository experienceLogRepository;
    private final TaskRepository taskRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ExperienceAwarder experienceAwarder;
    private final ReadBack readBack;

    public ExperienceService(
            TaskSkillRepository taskSkillRepository,
            ExperienceLogRepository experienceLogRepository,
            TaskRepository taskRepository,
            SkillRepository skillRepository,
            UserRepository userRepository,
            ExperienceAwarder experienceAwarder,
            ReadBack readBack) {
        this.taskSkillRepository = taskSkillRepository;
        this.experienceLogRepository = experienceLogRepository;
        this.taskRepository = taskRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.experienceAwarder = experienceAwarder;
        this.readBack = readBack;
    }

    @Transactional(readOnly = true)
    public List<TaskSkillResponse> getTaskSkills(Integer taskId) {
        requireTask(taskId);
        return taskSkillRepository.findByTaskIdWithSkill(taskId).stream()
                .map(TaskSkillResponse::from)
                .toList();
    }

    @Transactional
    public List<TaskSkillResponse> setTaskSkills(Integer taskId, TaskSkillsSetRequest request) {
        // The lock keeps a concurrent completion of the same task from awarding the old reward set.
        Task task = taskRepository
                .findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("Task with id " + taskId + " not found"));

        for (TaskSkillsSetRequest.Item item : request.skills()) {
            if (!skillRepository.existsById(item.skillId())) {
                throw new NotFoundException("Skill " + item.skillId() + " not found");
            }
        }

        taskSkillRepository.deleteAllByTaskId(taskId);
        for (TaskSkillsSetRequest.Item item : request.skills()) {
            taskSkillRepository.save(new TaskSkill(taskId, item.skillId(), item.expReward()));
        }
        taskSkillRepository.flush();

        // Rewards attached to an already finished task are paid out immediately; the awarder is
        // idempotent per (task, user, skill), so previously credited skills are not paid twice.
        if (task.getStatus() == TaskStatus.DONE) {
            experienceAwarder.awardForTask(task);
        }

        readBack.flushAndDetach();
        return taskSkillRepository.findByTaskIdWithSkill(taskId).stream()
                .map(TaskSkillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExperienceLogResponse> getUserLog(Integer userId, int limit, int offset) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User " + userId + " not found");
        }
        return experienceLogRepository
                .findByUserIdNewestFirst(userId, OffsetLimit.of(limit, offset, Sort.unsorted()))
                .stream()
                .map(ExperienceLogResponse::from)
                .toList();
    }

    private void requireTask(Integer taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new NotFoundException("Task with id " + taskId + " not found");
        }
    }
}
