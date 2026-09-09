package com.skilltracker.service;

import com.skilltracker.config.GitHubProperties;
import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskAttachment;
import com.skilltracker.domain.TaskLabel;
import com.skilltracker.domain.TaskRelation;
import com.skilltracker.domain.User;
import com.skilltracker.dto.LabelResponse;
import com.skilltracker.dto.RelatedTaskResponse;
import com.skilltracker.dto.TaskAttachmentResponse;
import com.skilltracker.dto.TaskDetailResponse;
import com.skilltracker.dto.TaskListItemResponse;
import com.skilltracker.dto.UserSummaryResponse;
import com.skilltracker.repository.TaskAttachmentRepository;
import com.skilltracker.repository.TaskIdCount;
import com.skilltracker.repository.TaskLabelRepository;
import com.skilltracker.repository.TaskRelationRepository;
import com.skilltracker.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Assembles task payloads.
 *
 * <p>Labels, attachments and relations are loaded in one batched query per collection rather than
 * per task, which is what the previous {@code selectinload} strategy did and keeps the list endpoint
 * free of N+1 queries.
 */
@Component
public class TaskMapper {

    private final TaskLabelRepository taskLabelRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskRelationRepository taskRelationRepository;
    private final UserRepository userRepository;
    private final GitHubProperties gitHubProperties;

    public TaskMapper(
            TaskLabelRepository taskLabelRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            TaskRelationRepository taskRelationRepository,
            UserRepository userRepository,
            GitHubProperties gitHubProperties) {
        this.taskLabelRepository = taskLabelRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.taskRelationRepository = taskRelationRepository;
        this.userRepository = userRepository;
        this.gitHubProperties = gitHubProperties;
    }

    public List<TaskListItemResponse> toListItems(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Integer> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Integer, List<LabelResponse>> labels = labelsByTaskId(taskIds);
        Map<Integer, Long> attachmentCounts = attachmentCountsByTaskId(taskIds);
        Map<Integer, Long> relationCounts = relationCountsByTaskId(taskIds);
        Map<Integer, UserSummaryResponse> people = peopleOf(tasks);

        return tasks.stream()
                .map(task -> new TaskListItemResponse(
                        task.getId(),
                        task.getTitle(),
                        task.getStatus(),
                        task.getDifficulty(),
                        task.getDeadline(),
                        people.get(task.getCreatorId()),
                        people.get(task.getAssigneeId()),
                        labels.getOrDefault(task.getId(), List.of()),
                        attachmentCounts.getOrDefault(task.getId(), 0L).intValue(),
                        relationCounts.getOrDefault(task.getId(), 0L).intValue(),
                        task.getGithubIssueNumber(),
                        githubUrl(task),
                        task.getCreatedAt(),
                        task.getUpdatedAt()))
                .toList();
    }

    public TaskDetailResponse toDetail(Task task) {
        List<Integer> taskIds = List.of(task.getId());
        List<LabelResponse> labels = labelsByTaskId(taskIds).getOrDefault(task.getId(), List.of());
        List<TaskAttachmentResponse> attachments = taskAttachmentRepository.findByTaskIdsWithAuthor(taskIds).stream()
                .map(TaskAttachmentResponse::from)
                .toList();
        List<RelatedTaskResponse> relatedTasks = relatedTasks(task.getId());
        Map<Integer, UserSummaryResponse> people = peopleOf(List.of(task));

        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getDifficulty(),
                task.getDeadline(),
                people.get(task.getCreatorId()),
                people.get(task.getAssigneeId()),
                labels,
                attachments.size(),
                relatedTasks.size(),
                task.getGithubIssueNumber(),
                githubUrl(task),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getDescription(),
                attachments,
                relatedTasks,
                task.getCreatorId(),
                task.getAssigneeId(),
                task.getApprovedById(),
                task.getApprovedAt());
    }

    /** Both sides of every pair the task takes part in, own-side first. */
    public List<RelatedTaskResponse> relatedTasks(Integer taskId) {
        List<TaskRelation> relations = taskRelationRepository.findInvolvingWithTasks(List.of(taskId));

        List<RelatedTaskResponse> related = new ArrayList<>(relations.size());
        relations.stream()
                .filter(relation -> relation.getLeftTaskId().equals(taskId))
                .forEach(relation -> related.add(RelatedTaskResponse.from(relation.otherSideOf(taskId))));
        relations.stream()
                .filter(relation -> !relation.getLeftTaskId().equals(taskId))
                .forEach(relation -> related.add(RelatedTaskResponse.from(relation.otherSideOf(taskId))));
        return related;
    }

    public List<TaskAttachmentResponse> attachments(Integer taskId) {
        return taskAttachmentRepository.findByTaskIdsWithAuthor(List.of(taskId)).stream()
                .map(TaskAttachmentResponse::from)
                .toList();
    }

    /** Loads every creator and assignee referenced by the given tasks in one query. */
    private Map<Integer, UserSummaryResponse> peopleOf(List<Task> tasks) {
        Set<Integer> userIds = new java.util.LinkedHashSet<>();
        tasks.forEach(task -> {
            userIds.add(task.getCreatorId());
            if (task.getAssigneeId() != null) {
                userIds.add(task.getAssigneeId());
            }
        });
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, UserSummaryResponse> people = new HashMap<>();
        for (User user : userRepository.findAllByIdIn(userIds)) {
            people.put(user.getId(), UserSummaryResponse.from(user));
        }
        return people;
    }

    private String githubUrl(Task task) {
        if (task.getGithubIssueNumber() == null) {
            return null;
        }
        return "https://github.com/" + gitHubProperties.repo() + "/issues/" + task.getGithubIssueNumber();
    }

    private Map<Integer, List<LabelResponse>> labelsByTaskId(List<Integer> taskIds) {
        return taskLabelRepository.findByTaskIdsWithLabel(taskIds).stream()
                .collect(Collectors.groupingBy(
                        TaskLabel::getTaskId,
                        Collectors.mapping(
                                taskLabel -> LabelResponse.from(taskLabel.getLabel()), Collectors.toList())));
    }

    private Map<Integer, Long> attachmentCountsByTaskId(List<Integer> taskIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (TaskIdCount row : taskAttachmentRepository.countByTaskIds(taskIds)) {
            counts.put(row.getTaskId(), row.getTotal());
        }
        return counts;
    }

    private Map<Integer, Long> relationCountsByTaskId(List<Integer> taskIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (TaskRelation relation : taskRelationRepository.findInvolving(taskIds)) {
            counts.merge(relation.getLeftTaskId(), 1L, Long::sum);
            counts.merge(relation.getRightTaskId(), 1L, Long::sum);
        }
        counts.keySet().retainAll(taskIds);
        return counts;
    }

    /** Attachment payload for a single freshly created row. */
    public TaskAttachmentResponse toAttachment(TaskAttachment attachment) {
        return TaskAttachmentResponse.from(attachment);
    }
}
