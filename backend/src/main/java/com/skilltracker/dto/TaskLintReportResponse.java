package com.skilltracker.dto;

import java.util.List;

public record TaskLintReportResponse(Integer taskId, List<TaskLintWarningResponse> warnings) {}
