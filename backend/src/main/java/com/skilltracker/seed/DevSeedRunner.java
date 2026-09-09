package com.skilltracker.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the dev seed and lets the process exit. Activated with the {@code seed} profile, which also
 * turns off the web server, so {@code SPRING_PROFILES_ACTIVE=seed} is a one-shot command.
 */
@Component
@Profile("seed")
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final DevSeedService devSeedService;

    public DevSeedRunner(DevSeedService devSeedService) {
        this.devSeedService = devSeedService;
    }

    @Override
    public void run(ApplicationArguments args) {
        devSeedService.seedUsers().forEach(log::info);
        devSeedService.seedSkills().forEach(log::info);
    }
}
