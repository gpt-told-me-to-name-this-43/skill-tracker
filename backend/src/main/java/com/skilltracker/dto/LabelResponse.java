package com.skilltracker.dto;

import com.skilltracker.domain.Label;
import java.time.LocalDateTime;

public record LabelResponse(Integer id, String name, String color, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(
                label.getId(), label.getName(), label.getColor(), label.getCreatedAt(), label.getUpdatedAt());
    }
}
