package com.skilltracker.dto;

public record UserSkillResponse(
        SkillResponse skill, int experience, int level, int currentLevelXp, int nextLevelXp, int progressToNextLevel) {}
