package com.skilltracker.dto;

import com.skilltracker.domain.ExperienceLog;
import java.time.LocalDateTime;

public record ExperienceLogResponse(
        Integer id, Integer userId, Integer skillId, Integer taskId, int amount, LocalDateTime createdAt) {

    public static ExperienceLogResponse from(ExperienceLog log) {
        return new ExperienceLogResponse(
                log.getId(), log.getUserId(), log.getSkillId(), log.getTaskId(), log.getAmount(), log.getCreatedAt());
    }
}
