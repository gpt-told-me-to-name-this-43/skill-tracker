package com.skilltracker.controller;

import com.skilltracker.dto.ExperienceLogResponse;
import com.skilltracker.service.ExperienceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ExperienceController {

    private final ExperienceService experienceService;

    public ExperienceController(ExperienceService experienceService) {
        this.experienceService = experienceService;
    }

    @GetMapping("/users/{userId}/experience-log")
    public List<ExperienceLogResponse> getUserExperienceLog(
            @PathVariable Integer userId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return experienceService.getUserLog(userId, limit, offset);
    }
}
