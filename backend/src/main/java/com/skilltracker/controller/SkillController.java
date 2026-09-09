package com.skilltracker.controller;

import com.skilltracker.dto.SkillCreateRequest;
import com.skilltracker.dto.SkillResponse;
import com.skilltracker.dto.SkillUpdateRequest;
import com.skilltracker.dto.UserProgressResponse;
import com.skilltracker.dto.UserSkillAssignRequest;
import com.skilltracker.dto.UserSkillResponse;
import com.skilltracker.service.SkillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/skills")
    public List<SkillResponse> listSkills(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return skillService.list(limit, offset);
    }

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillResponse createSkill(@Valid @RequestBody SkillCreateRequest request) {
        return skillService.create(request);
    }

    @GetMapping("/skills/{skillId}")
    public SkillResponse getSkill(@PathVariable Integer skillId) {
        return skillService.get(skillId);
    }

    @PatchMapping("/skills/{skillId}")
    public SkillResponse updateSkill(@PathVariable Integer skillId, @Valid @RequestBody SkillUpdateRequest request) {
        return skillService.update(skillId, request);
    }

    @DeleteMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable Integer skillId) {
        skillService.delete(skillId);
    }

    @PostMapping("/users/{userId}/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSkillResponse assignSkillToUser(
            @PathVariable Integer userId, @Valid @RequestBody UserSkillAssignRequest request) {
        return skillService.assignSkillToUser(userId, request.skillId());
    }

    @GetMapping("/users/{userId}/skills")
    public List<UserSkillResponse> getUserSkills(@PathVariable Integer userId) {
        return skillService.getUserSkills(userId);
    }

    @GetMapping("/users/{userId}/progress")
    public UserProgressResponse getUserProgress(@PathVariable Integer userId) {
        return skillService.getUserProgress(userId);
    }
}
