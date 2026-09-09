package com.skilltracker.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for tests that exercise the application against a real PostgreSQL schema. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupportConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        TestDatabase.apply(registry);
    }

    @BeforeEach
    void resetDatabase() {
        databaseCleaner.clean();
    }
}
