package com.skilltracker.dto;

import com.skilltracker.json.Patch;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDateTime;

public record TaskUpdateRequest(
        Patch<String> title, Patch<String> description, Patch<Integer> difficulty, Patch<LocalDateTime> deadline) {

    public TaskUpdateRequest {
        title = title == null ? Patch.absent() : title;
        description = description == null ? Patch.absent() : description;
        difficulty = difficulty == null ? Patch.absent() : difficulty;
        deadline = deadline == null ? Patch.absent() : deadline;

        if (title.isPresent() && title.value() != null) {
            title = Patch.of(title.value().trim());
        }
    }

    @AssertTrue(message = "Title must not be empty")
    public boolean isTitleUsable() {
        String value = title.value();
        return value == null || (!value.isEmpty() && value.length() <= 255);
    }

    @AssertTrue(message = "Input should be between 1 and 5")
    public boolean isDifficultyInRange() {
        Integer value = difficulty.value();
        return value == null || (value >= 1 && value <= 5);
    }
}
