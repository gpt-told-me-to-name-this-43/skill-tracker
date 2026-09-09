package com.skilltracker.service;

import com.skilltracker.domain.Label;
import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskAttachment;
import com.skilltracker.domain.TaskLabel;
import com.skilltracker.domain.TaskRelation;
import com.skilltracker.domain.TaskStatus;
import com.skilltracker.dto.LabelCreateRequest;
import com.skilltracker.dto.LabelResponse;
import com.skilltracker.dto.RelatedTaskResponse;
import com.skilltracker.dto.TaskAttachmentCreateRequest;
import com.skilltracker.dto.TaskAttachmentResponse;
import com.skilltracker.dto.TaskCreateRequest;
import com.skilltracker.dto.TaskDetailResponse;
import com.skilltracker.dto.TaskListItemResponse;
import com.skilltracker.dto.TaskUpdateRequest;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.exception.ConflictException;
import com.skilltracker.exception.NotFoundException;
import com.skilltracker.repository.LabelRepository;
import com.skilltracker.repository.OffsetLimit;
import com.skilltracker.repository.TaskAttachmentRepository;
import com.skilltracker.repository.TaskLabelRepository;
import com.skilltracker.repository.TaskRelationRepository;
import com.skilltracker.repository.TaskRepository;
import com.skilltracker.repository.TaskSpecifications;
import com.skilltracker.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskRelationRepository taskRelationRepository;
    private final ExperienceAwarder experienceAwarder;
    private final AttachmentStorage attachmentStorage;
    private final TaskMapper taskMapper;
    private final ReadBack readBack;
    private final Clock clock;

    public TaskService(
            TaskRepository taskRepository,
            UserRepository userRepository,
            LabelRepository labelRepository,
            TaskLabelRepository taskLabelRepository,
            TaskAttachmentRepository taskAttachmentRepository,
            TaskRelationRepository taskRelationRepository,
            ExperienceAwarder experienceAwarder,
            AttachmentStorage attachmentStorage,
            TaskMapper taskMapper,
            ReadBack readBack,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.labelRepository = labelRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.taskAttachmentRepository = taskAttachmentRepository;
        this.taskRelationRepository = taskRelationRepository;
        this.experienceAwarder = experienceAwarder;
        this.attachmentStorage = attachmentStorage;
        this.taskMapper = taskMapper;
        this.readBack = readBack;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskListItemResponse> getTasks(
            TaskStatus status, Integer assigneeId, Integer difficulty, int limit, int offset) {
        return taskMapper.toListItems(taskRepository
                .findAll(
                        TaskSpecifications.matching(status, assigneeId, difficulty),
                        OffsetLimit.of(limit, offset, Sort.by(Sort.Direction.DESC, "id")))
                .getContent());
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTask(Integer taskId) {
        return taskMapper.toDetail(requireTask(taskId));
    }

    @Transactional
    public TaskDetailResponse createTask(TaskCreateRequest request, Integer creatorId) {
        LocalDateTime deadline = validateDeadline(request.deadline());
        requireUserExists(creatorId);
        requireUserExists(request.assigneeId());

        Task task = new Task(request.title(), creatorId);
        task.setDescription(request.description());
        task.setDifficulty(request.difficulty());
        task.setDeadline(deadline);
        task.setAssigneeId(request.assigneeId());

        taskRepository.save(task);
        readBack.flushAndDetach();
        return taskMapper.toDetail(requireTask(task.getId()));
    }

    @Transactional
    public TaskDetailResponse updateTask(Integer taskId, TaskUpdateRequest request) {
        Task task = requireTask(taskId);

        if (request.title().isPresent() && request.title().value() != null) {
            task.setTitle(request.title().value());
        }
        if (request.description().isPresent()) {
            task.setDescription(request.description().value());
        }
        if (request.difficulty().isPresent() && request.difficulty().value() != null) {
            task.setDifficulty(request.difficulty().value());
        }
        if (request.deadline().isPresent()) {
            task.setDeadline(validateDeadline(request.deadline().value()));
        }

        taskRepository.save(task);
        readBack.flushAndDetach();
        return taskMapper.toDetail(requireTask(taskId));
    }

    /**
     * Moves a task through the workflow and, on the first transition into {@code done}, pays out its
     * XP in the same transaction. The task row is locked so two concurrent completions cannot both
     * observe a non-done status and award twice.
     */
    @Transactional
    public TaskDetailResponse changeStatus(Integer taskId, TaskStatus newStatus) {
        Task locked = taskRepository
                .findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("Task with id " + taskId + " not found"));

        TaskStatus oldStatus = locked.getStatus();
        validateStatusTransition(oldStatus, newStatus);

        if (oldStatus == TaskStatus.REVIEW && newStatus == TaskStatus.DONE && locked.getApprovedAt() == null) {
            throw new BadRequestException("Task must be approved before moving to done");
        }

        locked.setStatus(newStatus);
        if (shouldClearApproval(oldStatus, newStatus)) {
            locked.clearApproval();
        }
        taskRepository.saveAndFlush(locked);

        if (oldStatus != TaskStatus.DONE && newStatus == TaskStatus.DONE) {
            experienceAwarder.awardForTask(locked);
        }

        readBack.flushAndDetach();
        return taskMapper.toDetail(requireTask(taskId));
    }

    @Transactional
    public TaskDetailResponse assignTask(Integer taskId, Integer assigneeId) {
        Task task = requireTask(taskId);
        requireUserExists(assigneeId);

        task.setAssigneeId(assigneeId);
        taskRepository.save(task);
        readBack.flushAndDetach();
        return taskMapper.toDetail(requireTask(taskId));
    }

    @Transactional
    public TaskDetailResponse approveTask(Integer taskId, Integer approverId) {
        Task task = requireTask(taskId);
        requireUserExists(approverId);

        if (task.getStatus() != TaskStatus.REVIEW) {
            throw new BadRequestException("Only tasks in review can be approved");
        }

        task.approve(approverId, LocalDateTime.now(clock));
        taskRepository.save(task);
        readBack.flushAndDetach();
        return taskMapper.toDetail(requireTask(taskId));
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> listLabels() {
        return labelRepository.findAllByOrderByNameAsc().stream()
                .map(LabelResponse::from)
                .toList();
    }

    @Transactional
    public LabelResponse createLabel(LabelCreateRequest request) {
        if (labelRepository.findByNameIgnoreCase(request.name()).isPresent()) {
            throw new ConflictException("Label name already exists");
        }
        return LabelResponse.from(labelRepository.save(new Label(request.name(), request.color())));
    }

    @Transactional
    public TaskDetailResponse setTaskLabels(Integer taskId, List<Integer> labelIds) {
        requireTask(taskId);

        List<Label> labels = labelIds.isEmpty() ? List.of() : labelRepository.findByIdIn(labelIds);
        if (labels.size() != labelIds.size()) {
            throw new NotFoundException("One or more labels were not found");
        }

        taskLabelRepository.deleteAllByTaskId(taskId);
        for (Integer labelId : labelIds) {
            taskLabelRepository.save(new TaskLabel(taskId, labelId));
        }
        readBack.flushAndDetach();

        return taskMapper.toDetail(requireTask(taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentResponse> listAttachments(Integer taskId) {
        requireTask(taskId);
        return taskMapper.attachments(taskId);
    }

    @Transactional
    public TaskAttachmentResponse createAttachment(
            Integer taskId, TaskAttachmentCreateRequest request, Integer createdById) {
        requireTask(taskId);
        requireUserExists(createdById);

        TaskAttachment attachment =
                taskAttachmentRepository.save(new TaskAttachment(taskId, request.name(), request.url(), createdById));
        readBack.flushAndDetach();
        return taskMapper.toAttachment(
                taskAttachmentRepository.findByIdWithAuthor(attachment.getId()).orElseThrow());
    }

    @Transactional
    public TaskAttachmentResponse createUploadedAttachment(
            Integer taskId, String filename, byte[] content, Integer createdById) {
        requireTask(taskId);
        requireUserExists(createdById);
        if (content == null || content.length == 0) {
            throw new BadRequestException("Uploaded file is empty");
        }

        String displayName = attachmentStorage.displayName(filename);
        String url = attachmentStorage.store(displayName, content);

        TaskAttachment attachment =
                taskAttachmentRepository.save(new TaskAttachment(taskId, displayName, url, createdById));
        readBack.flushAndDetach();
        return taskMapper.toAttachment(
                taskAttachmentRepository.findByIdWithAuthor(attachment.getId()).orElseThrow());
    }

    @Transactional
    public void deleteAttachment(Integer taskId, Integer attachmentId) {
        requireTask(taskId);
        TaskAttachment attachment = taskAttachmentRepository
                .findByTaskIdAndId(taskId, attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
        taskAttachmentRepository.delete(attachment);
    }

    @Transactional(readOnly = true)
    public List<RelatedTaskResponse> listRelatedTasks(Integer taskId) {
        requireTask(taskId);
        return taskMapper.relatedTasks(taskId);
    }

    @Transactional
    public List<RelatedTaskResponse> setRelatedTasks(Integer taskId, List<Integer> relatedTaskIds) {
        requireTask(taskId);
        if (relatedTaskIds.contains(taskId)) {
            throw new BadRequestException("Task cannot be related to itself");
        }

        Set<Integer> existing =
                relatedTaskIds.isEmpty() ? Set.of() : new HashSet<>(taskRepository.findExistingIds(relatedTaskIds));
        if (existing.size() != relatedTaskIds.size()) {
            throw new NotFoundException("One or more related tasks were not found");
        }

        taskRelationRepository.deleteInvolving(taskId);
        for (Integer relatedTaskId : relatedTaskIds) {
            taskRelationRepository.save(TaskRelation.between(taskId, relatedTaskId));
        }
        readBack.flushAndDetach();

        return taskMapper.relatedTasks(taskId);
    }

    private void validateStatusTransition(TaskStatus oldStatus, TaskStatus newStatus) {
        if (oldStatus == newStatus) {
            return;
        }
        if (newStatus == TaskStatus.DONE && oldStatus != TaskStatus.REVIEW) {
            throw new BadRequestException("Task can be moved to done only from review");
        }
    }

    private boolean shouldClearApproval(TaskStatus oldStatus, TaskStatus newStatus) {
        if (oldStatus == newStatus) {
            return false;
        }
        if (newStatus == TaskStatus.REVIEW) {
            return true;
        }
        return (oldStatus == TaskStatus.REVIEW || oldStatus == TaskStatus.DONE) && newStatus != TaskStatus.DONE;
    }

    /** Deadlines are stored as naive UTC and must lie in the future. */
    private LocalDateTime validateDeadline(LocalDateTime deadline) {
        if (deadline == null) {
            return null;
        }
        if (!deadline.isAfter(LocalDateTime.now(clock))) {
            throw new BadRequestException("Deadline must be in the future");
        }
        return deadline;
    }

    private Task requireTask(Integer taskId) {
        return taskRepository
                .findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task with id " + taskId + " not found"));
    }

    private void requireUserExists(Integer userId) {
        if (userId != null && !userRepository.existsById(userId)) {
            throw new NotFoundException("User with id " + userId + " not found");
        }
    }
}
