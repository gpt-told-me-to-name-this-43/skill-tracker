package com.skilltracker.dto;

import com.skilltracker.domain.Task;
import com.skilltracker.domain.TaskStatus;

public record RelatedTaskResponse(Integer id, String title, TaskStatus status) {

    public static RelatedTaskResponse from(Task task) {
        return new RelatedTaskResponse(task.getId(), task.getTitle(), task.getStatus());
    }
}
