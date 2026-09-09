package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.skilltracker.service.SkillProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SkillProgressTest {

    @ParameterizedTest
    @CsvSource({
        "0, 1, 0, 100, 0",
        "50, 1, 0, 100, 50",
        "99, 1, 0, 100, 99",
        "100, 2, 100, 200, 0",
        "200, 3, 200, 300, 0",
        "250, 3, 200, 300, 50"
    })
    void levelBoundaries(int experience, int level, int currentLevelXp, int nextLevelXp, int progress) {
        SkillProgress result = SkillProgress.forExperience(experience);

        assertThat(result.level()).isEqualTo(level);
        assertThat(result.currentLevelXp()).isEqualTo(currentLevelXp);
        assertThat(result.nextLevelXp()).isEqualTo(nextLevelXp);
        assertThat(result.progressToNextLevel()).isEqualTo(progress);
    }

    @Test
    void averageLevelIsRoundedToOneDecimal() {
        assertThat(SkillProgress.averageLevel(0, 0)).isEqualTo(0.0);
        assertThat(SkillProgress.averageLevel(4, 2)).isEqualTo(2.0);
        assertThat(SkillProgress.averageLevel(7, 2)).isEqualTo(3.5);
        assertThat(SkillProgress.averageLevel(10, 3)).isEqualTo(3.3);
        assertThat(SkillProgress.averageLevel(11, 3)).isEqualTo(3.7);
    }
}
