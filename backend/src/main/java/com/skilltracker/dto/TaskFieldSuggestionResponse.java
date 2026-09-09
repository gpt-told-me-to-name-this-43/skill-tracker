package com.skilltracker.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The deadline stays offset-aware (UTC) because the previous implementation derived it from an
 * estimate in days rather than reading it back from the database.
 */
public record TaskFieldSuggestionResponse(
        int difficulty, OffsetDateTime deadline, List<LabelResponse> labels, List<TaskSkillResponse> skills) {}
