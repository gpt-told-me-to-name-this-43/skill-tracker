package com.skilltracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Level and progress derived from an XP total; one level is worth 100 XP. */
public record SkillProgress(int level, int currentLevelXp, int nextLevelXp, int progressToNextLevel) {

    private static final int XP_PER_LEVEL = 100;

    public static SkillProgress forExperience(int experience) {
        int level = experience / XP_PER_LEVEL + 1;
        int currentLevelXp = (level - 1) * XP_PER_LEVEL;
        return new SkillProgress(level, currentLevelXp, level * XP_PER_LEVEL, experience - currentLevelXp);
    }

    /** Averages levels to one decimal, rounding half to even like the previous implementation. */
    public static double averageLevel(int totalLevel, int skillsCount) {
        if (skillsCount == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(totalLevel)
                .divide(BigDecimal.valueOf(skillsCount), 10, RoundingMode.HALF_EVEN)
                .setScale(1, RoundingMode.HALF_EVEN)
                .doubleValue();
    }
}
