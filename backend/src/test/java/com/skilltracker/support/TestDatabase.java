package com.skilltracker.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides the PostgreSQL instance the integration tests run against.
 *
 * <p>By default a Testcontainers instance is started once per JVM. Environments without a container
 * runtime can point the suite at an existing server with {@code -Dtest.datasource.url=...} (or the
 * {@code TEST_DATASOURCE_URL} environment variable), which keeps the tests reproducible on
 * developer machines and in CI alike.
 */
public final class TestDatabase {

    private static final String URL_PROPERTY = "test.datasource.url";
    private static final String USERNAME_PROPERTY = "test.datasource.username";
    private static final String PASSWORD_PROPERTY = "test.datasource.password";

    private static PostgreSQLContainer<?> container;

    private TestDatabase() {}

    public static synchronized void apply(DynamicPropertyRegistry registry) {
        String externalUrl = setting(URL_PROPERTY, "TEST_DATASOURCE_URL");
        if (externalUrl != null) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add(
                    "spring.datasource.username",
                    () -> orDefault(setting(USERNAME_PROPERTY, "TEST_DATASOURCE_USERNAME"), "postgres"));
            registry.add(
                    "spring.datasource.password",
                    () -> orDefault(setting(PASSWORD_PROPERTY, "TEST_DATASOURCE_PASSWORD"), "postgres"));
            return;
        }

        PostgreSQLContainer<?> postgres = startedContainer();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static PostgreSQLContainer<?> startedContainer() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16");
            container.start();
        }
        return container;
    }

    private static String setting(String systemProperty, String environmentVariable) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? null : value;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
