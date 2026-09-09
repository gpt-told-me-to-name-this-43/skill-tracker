package com.skilltracker.dto;

public record TaskLintWarningResponse(String code, String field, String severity, String message) {}
