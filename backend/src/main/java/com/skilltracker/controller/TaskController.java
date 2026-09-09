package com.skilltracker.controller;

import com.skilltracker.domain.TaskStatus;
import com.skilltracker.domain.User;
import com.skilltracker.dto.LabelCreateRequest;
import com.skilltracker.dto.LabelResponse;
import com.skilltracker.dto.RelatedTaskResponse;
import com.skilltracker.dto.TaskAnalyzeRequest;
import com.skilltracker.dto.TaskAssignRequest;
import com.skilltracker.dto.TaskAttachmentCreateRequest;
import com.skilltracker.dto.TaskAttachmentResponse;
import com.skilltracker.dto.TaskCreateRequest;
import com.skilltracker.dto.TaskDetailResponse;
import com.skilltracker.dto.TaskFieldSuggestionResponse;
import com.skilltracker.dto.TaskLabelsSetRequest;
import com.skilltracker.dto.TaskLintReportResponse;
import com.skilltracker.dto.TaskListItemResponse;
import com.skilltracker.dto.TaskRelatedSetRequest;
import com.skilltracker.dto.TaskSkillResponse;
import com.skilltracker.dto.TaskSkillsSetRequest;
import com.skilltracker.dto.TaskStatusUpdateRequest;
import com.skilltracker.dto.TaskUpdateRequest;
import com.skilltracker.exception.BadRequestException;
import com.skilltracker.security.CurrentUser;
import com.skilltracker.service.ExperienceService;
import com.skilltracker.service.TaskLintService;
import com.skilltracker.service.TaskService;
import com.skilltracker.service.TaskSuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TaskController {

    private static final String UPLOAD_FIELD = "file";

    private final TaskService taskService;
    private final TaskLintService taskLintService;
    private final ExperienceService experienceService;
    private final TaskSuggestionService taskSuggestionService;

    public TaskController(
            TaskService taskService,
            TaskLintService taskLintService,
            ExperienceService experienceService,
            TaskSuggestionService taskSuggestionService) {
        this.taskService = taskService;
        this.taskLintService = taskLintService;
        this.experienceService = experienceService;
        this.taskSuggestionService = taskSuggestionService;
    }

    @GetMapping("/tasks")
    public List<TaskListItemResponse> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(name = "assignee_id", required = false) Integer assigneeId,
            @RequestParam(required = false) @Min(1) @Max(5) Integer difficulty,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return taskService.getTasks(status, assigneeId, difficulty, limit, offset);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDetailResponse createTask(@Valid @RequestBody TaskCreateRequest request, @CurrentUser User currentUser) {
        return taskService.createTask(request, currentUser.getId());
    }

    /** Declared before {@code /tasks/{taskId}} so "analyze" is not read as an id. */
    @PostMapping("/tasks/analyze")
    public TaskFieldSuggestionResponse analyzeTask(@Valid @RequestBody TaskAnalyzeRequest request) {
        return taskSuggestionService.analyze(request);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskDetailResponse getTask(@PathVariable Integer taskId) {
        return taskService.getTask(taskId);
    }

    @GetMapping("/tasks/{taskId}/lint")
    public TaskLintReportResponse lintTask(@PathVariable Integer taskId) {
        return taskLintService.lintTask(taskId);
    }

    @GetMapping("/tasks/{taskId}/skills")
    public List<TaskSkillResponse> getTaskSkills(@PathVariable Integer taskId) {
        return experienceService.getTaskSkills(taskId);
    }

    @PutMapping("/tasks/{taskId}/skills")
    public List<TaskSkillResponse> setTaskSkills(
            @PathVariable Integer taskId, @Valid @RequestBody TaskSkillsSetRequest request) {
        return experienceService.setTaskSkills(taskId, request);
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskDetailResponse updateTask(@PathVariable Integer taskId, @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.updateTask(taskId, request);
    }

    @PatchMapping("/tasks/{taskId}/status")
    public TaskDetailResponse changeTaskStatus(
            @PathVariable Integer taskId, @Valid @RequestBody TaskStatusUpdateRequest request) {
        return taskService.changeStatus(taskId, request.status());
    }

    @PatchMapping("/tasks/{taskId}/approve")
    public TaskDetailResponse approveTask(@PathVariable Integer taskId, @CurrentUser User currentUser) {
        return taskService.approveTask(taskId, currentUser.getId());
    }

    @PatchMapping("/tasks/{taskId}/assign")
    public TaskDetailResponse assignTask(@PathVariable Integer taskId, @RequestBody TaskAssignRequest request) {
        return taskService.assignTask(taskId, request.assigneeId());
    }

    @GetMapping("/labels")
    public List<LabelResponse> listLabels() {
        return taskService.listLabels();
    }

    @PostMapping("/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse createLabel(@Valid @RequestBody LabelCreateRequest request) {
        return taskService.createLabel(request);
    }

    @PutMapping("/tasks/{taskId}/labels")
    public TaskDetailResponse setTaskLabels(
            @PathVariable Integer taskId, @Valid @RequestBody TaskLabelsSetRequest request) {
        return taskService.setTaskLabels(taskId, request.labelIds());
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public List<TaskAttachmentResponse> listAttachments(@PathVariable Integer taskId) {
        return taskService.listAttachments(taskId);
    }

    @PostMapping("/tasks/{taskId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskAttachmentResponse createAttachment(
            @PathVariable Integer taskId,
            @Valid @RequestBody TaskAttachmentCreateRequest request,
            @CurrentUser User currentUser) {
        return taskService.createAttachment(taskId, request, currentUser.getId());
    }

    @PostMapping("/tasks/{taskId}/attachments/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskAttachmentResponse uploadAttachment(
            @PathVariable Integer taskId, HttpServletRequest request, @CurrentUser User currentUser) {
        MultipartFile file = extractUploadedFile(request);
        return taskService.createUploadedAttachment(
                taskId, file.getOriginalFilename(), readBytes(file), currentUser.getId());
    }

    @DeleteMapping("/tasks/{taskId}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Integer taskId, @PathVariable Integer attachmentId) {
        taskService.deleteAttachment(taskId, attachmentId);
    }

    @GetMapping("/tasks/{taskId}/related")
    public List<RelatedTaskResponse> listRelatedTasks(@PathVariable Integer taskId) {
        return taskService.listRelatedTasks(taskId);
    }

    @PutMapping("/tasks/{taskId}/related")
    public List<RelatedTaskResponse> setRelatedTasks(
            @PathVariable Integer taskId, @Valid @RequestBody TaskRelatedSetRequest request) {
        return taskService.setRelatedTasks(taskId, request.taskIds());
    }

    /**
     * Reads the {@code file} part through Spring's multipart support while keeping the 400 responses
     * the previous hand-rolled parser produced.
     */
    private MultipartFile extractUploadedFile(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            throw new BadRequestException("Expected multipart form data");
        }
        // The security filter chain wraps the request, so the multipart view has to be unwrapped.
        MultipartHttpServletRequest multipartRequest =
                WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipartRequest == null) {
            throw new BadRequestException("Multipart boundary is missing");
        }

        MultipartFile file = multipartRequest.getFile(UPLOAD_FIELD);
        if (file == null) {
            throw new BadRequestException("File field is missing");
        }
        return file;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
