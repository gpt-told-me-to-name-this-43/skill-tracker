package com.skilltracker.dto;

import com.skilltracker.domain.Skill;

public record SkillResponse(Integer id, String name, String description) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getDescription());
    }
}
