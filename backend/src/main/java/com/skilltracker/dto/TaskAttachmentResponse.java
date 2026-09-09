package com.skilltracker.dto;

import com.skilltracker.domain.TaskAttachment;
import java.time.LocalDateTime;

public record TaskAttachmentResponse(
        Integer id, String name, String url, UserSummaryResponse createdBy, LocalDateTime createdAt) {

    public static TaskAttachmentResponse from(TaskAttachment attachment) {
        return new TaskAttachmentResponse(
                attachment.getId(),
                attachment.getName(),
                attachment.getUrl(),
                UserSummaryResponse.from(attachment.getCreatedBy()),
                attachment.getCreatedAt());
    }
}
