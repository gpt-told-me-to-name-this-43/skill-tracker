package com.skilltracker.dto;

import java.util.List;

public record UserProgressResponse(
        Integer userId, int totalExperience, int skillsCount, double averageLevel, List<UserSkillResponse> skills) {}
