package com.skilltracker.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers the shared test helpers with every {@code @SpringBootTest} context. */
@TestConfiguration
public class TestSupportConfiguration {

    @Bean
    DatabaseCleaner databaseCleaner() {
        return new DatabaseCleaner();
    }
}
