package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skilltracker.domain.User;
import com.skilltracker.repository.SkillRepository;
import com.skilltracker.repository.UserRepository;
import com.skilltracker.seed.DevSeed;
import com.skilltracker.seed.DevSeedService;
import com.skilltracker.support.ApiTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevSeedTest extends ApiTest {

    @Autowired
    private DevSeedService devSeedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedingCreatesTheDemoUserAndSkillCatalogue() {
        assertThat(devSeedService.seedUsers()).containsExactly("created: test@example.com");
        assertThat(devSeedService.seedSkills()).hasSize(DevSeed.SKILLS.size());

        User demo = userRepository.findByEmail("test@example.com").orElseThrow();
        assertThat(demo.getUsername()).isEqualTo("test");
        assertThat(demo.getRole()).isEqualTo("admin");
        assertThat(passwordEncoder.matches("password123", demo.getHashedPassword()))
                .isTrue();
        assertThat(skillRepository.count()).isEqualTo(DevSeed.SKILLS.size());
    }

    @Test
    void seedingTwiceCreatesNoDuplicates() {
        devSeedService.seedUsers();
        devSeedService.seedSkills();

        assertThat(devSeedService.seedUsers()).containsExactly("updated: test@example.com");
        assertThat(devSeedService.seedSkills())
                .allSatisfy(message -> assertThat(message).startsWith("skipped:"));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(skillRepository.count()).isEqualTo(DevSeed.SKILLS.size());
    }

    @Test
    void theSeededCredentialsCanLogIn() throws Exception {
        devSeedService.seedUsers();

        String token = login("test@example.com", "password123");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void theSkillCatalogueCoversTheCoreCompetencies() {
        List<String> names = DevSeed.SKILLS.stream().map(skill -> skill[0]).toList();

        assertThat(names)
                .contains(
                        "backend",
                        "frontend",
                        "database",
                        "api_design",
                        "devops",
                        "testing",
                        "documentation",
                        "debugging");
        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allSatisfy(name -> {
            assertThat(name).isEqualTo(name.trim());
            assertThat(name.length()).isBetween(1, 100);
        });
        assertThat(DevSeed.SKILLS).allSatisfy(skill -> assertThat(skill[1]).isNotBlank());
    }

    @Test
    void theDemoPasswordSatisfiesTheRegistrationRules() {
        assertThat(DevSeed.USERS.getFirst().password().length()).isGreaterThanOrEqualTo(8);
    }
}
