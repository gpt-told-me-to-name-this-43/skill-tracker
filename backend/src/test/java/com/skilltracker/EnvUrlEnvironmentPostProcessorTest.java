package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.skilltracker.config.EnvUrlEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * The {@code DATABASE_URL} / {@code REDIS_URL} environment interface is what existing {@code .env}
 * files and Compose overrides set, including the SQLAlchemy driver suffix.
 */
class EnvUrlEnvironmentPostProcessorTest {

    private final EnvUrlEnvironmentPostProcessor postProcessor = new EnvUrlEnvironmentPostProcessor();

    @Test
    void translatesTheSqlAlchemyStyleDatabaseUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql+asyncpg://postgres:postgres@postgres:5432/app");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://postgres:5432/app");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("postgres");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("postgres");
    }

    @Test
    void translatesAPlainPostgresUrlWithDefaultsAndEncodedCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://user:p%40ss@db.internal/app?sslmode=require");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.internal:5432/app?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss");
    }

    @Test
    void leavesAJdbcUrlAlone() {
        MockEnvironment environment =
                new MockEnvironment().withProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/app");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/app");
    }

    @Test
    void translatesTheRedisUrlIncludingTheDatabaseIndex() {
        MockEnvironment environment = new MockEnvironment().withProperty("REDIS_URL", "redis://redis:6379/2");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
        assertThat(environment.getProperty("spring.data.redis.database")).isEqualTo("2");
        assertThat(environment.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("false");
    }

    @Test
    void enablesTlsForRediss() {
        MockEnvironment environment =
                new MockEnvironment().withProperty("REDIS_URL", "rediss://:secret@redis.internal/0");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.data.redis.ssl.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.data.redis.port")).isEqualTo("6379");
        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo("secret");
    }

    @Test
    void withoutTheVariablesNothingIsOverridden() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(environment.getProperty("spring.data.redis.host")).isNull();
    }
}
