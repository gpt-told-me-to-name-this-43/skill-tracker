package com.skilltracker.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One validation failure, shaped like the pydantic entries the FastAPI backend returned so existing
 * clients and debugging tooling keep reading the same fields.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ValidationErrorDetail(String type, List<Object> loc, String msg, Object input) {}
