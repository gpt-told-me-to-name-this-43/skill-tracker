package com.skilltracker.dto;

import com.skilltracker.domain.TaskSkill;

public record TaskSkillResponse(SkillResponse skill, int expReward) {

    public static TaskSkillResponse from(TaskSkill taskSkill) {
        return new TaskSkillResponse(SkillResponse.from(taskSkill.getSkill()), taskSkill.getExpReward());
    }
}
