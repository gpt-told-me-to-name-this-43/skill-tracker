package com.skilltracker.service;

import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskSkill;
import com.skilltracker.dto.TaskLintReportResponse;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.repository.TaskLabelRepository;
import com.skilltracker.repository.TaskRepository;
import com.skilltracker.repository.TaskSkillRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskLintService {

    private final TaskRepository taskRepository;
    private final TaskSkillRepository taskSkillRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final Clock clock;

    public TaskLintService(
            TaskRepository taskRepository,
            TaskSkillRepository taskSkillRepository,
            TaskLabelRepository taskLabelRepository,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.taskSkillRepository = taskSkillRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.clock = clock;
    }

    /** Reports quality warnings for a task; it never blocks anything and never writes. */
    @Transactional(readOnly = true)
    public TaskLintReportResponse lintTask(Integer taskId) {
        Task task = taskRepository
                .findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task with id " + taskId + " not found"));

        List<Integer> rewards = taskSkillRepository.findByTaskIdOrderById(taskId).stream()
                .map(TaskSkill::getExpReward)
                .toList();
        int labelCount = taskLabelRepository.findByTaskIdOrderById(taskId).size();

        TaskLintRules.Input input = new TaskLintRules.Input(
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDifficulty(),
                task.getDeadline(),
                task.getAssigneeId(),
                labelCount,
                rewards);

        return new TaskLintReportResponse(taskId, TaskLintRules.lint(input, LocalDateTime.now(clock)));
    }
}
