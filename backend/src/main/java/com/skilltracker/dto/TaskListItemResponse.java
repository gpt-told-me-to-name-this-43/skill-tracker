package com.skilltracker.dto;

import com.skilltracker.domain.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;

public record TaskListItemResponse(
        Integer id,
        String title,
        TaskStatus status,
        int difficulty,
        LocalDateTime deadline,
        UserSummaryResponse creator,
        UserSummaryResponse assignee,
        List<LabelResponse> labels,
        int attachmentsCount,
        int relatedTasksCount,
        Integer githubIssueNumber,
        String githubUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
