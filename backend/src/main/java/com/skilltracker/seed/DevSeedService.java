package com.skilltracker.seed;

import com.skilltracker.domain.Skill;
import com.skilltracker.domain.User;
import com.skilltracker.repository.SkillRepository;
import com.skilltracker.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the demo user and skill catalogue. Running it twice never produces duplicates. */
@Service
public class DevSeedService {

    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSeedService(
            UserRepository userRepository, SkillRepository skillRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<String> seedUsers() {
        List<String> messages = new ArrayList<>();

        for (DevSeed.DevUser devUser : DevSeed.USERS) {
            Optional<User> byEmail = userRepository.findByEmail(devUser.email());
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                Optional<User> usernameOwner = userRepository
                        .findByUsername(devUser.username())
                        .filter(owner -> !owner.getId().equals(existing.getId()));
                if (usernameOwner.isPresent()) {
                    messages.add("skipped: username '" + devUser.username() + "' already exists");
                    continue;
                }

                existing.setUsername(devUser.username());
                existing.setHashedPassword(passwordEncoder.encode(devUser.password()));
                existing.setRole(devUser.role());
                userRepository.save(existing);
                messages.add("updated: " + devUser.email());
                continue;
            }

            if (userRepository.existsByUsername(devUser.username())) {
                messages.add("skipped: username '" + devUser.username() + "' already exists");
                continue;
            }

            userRepository.save(new User(
                    devUser.email(), devUser.username(), passwordEncoder.encode(devUser.password()), devUser.role()));
            messages.add("created: " + devUser.email());
        }

        return messages;
    }

    @Transactional
    public List<String> seedSkills() {
        List<String> messages = new ArrayList<>();

        for (String[] skill : DevSeed.SKILLS) {
            String name = skill[0];
            if (skillRepository.findByNameIgnoreCase(name).isPresent()) {
                messages.add("skipped: skill '" + name + "' already exists");
                continue;
            }
            skillRepository.save(new Skill(name, skill[1]));
            messages.add("created: skill '" + name + "'");
        }

        return messages;
    }
}
