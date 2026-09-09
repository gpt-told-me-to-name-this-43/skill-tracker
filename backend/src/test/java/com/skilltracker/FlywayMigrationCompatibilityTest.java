package com.skilltracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.skilltracker.support.IntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Both deployment paths have to work: a brand new database that Flyway builds from scratch, and a
 * database the retired Alembic chain already created and filled with data.
 *
 * <p>Each case runs in its own schema so it never touches the schema the application context uses.
 */
class FlywayMigrationCompatibilityTest extends IntegrationTest {

    private static final String FRESH_SCHEMA = "migration_fresh";
    private static final String LEGACY_SCHEMA = "migration_legacy";

    @Autowired
    private DataSource dataSource;

    @Test
    void aFreshDatabaseIsBuiltFromTheBaselineMigration() throws SQLException {
        recreateSchema(FRESH_SCHEMA);

        var result = flywayFor(FRESH_SCHEMA).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(tableExists(FRESH_SCHEMA, "tasks")).isTrue();
        assertThat(tableExists(FRESH_SCHEMA, "experience_logs")).isTrue();
        assertThat(scalar("select count(*) from " + FRESH_SCHEMA + ".labels")).isEqualTo(9L);
    }

    /**
     * The Alembic-created schema is adopted rather than rebuilt: the baseline migration must be
     * skipped and the existing rows must survive untouched.
     */
    @Test
    void anExistingAlembicDatabaseIsAdoptedWithoutDataLoss() throws SQLException {
        recreateSchema(LEGACY_SCHEMA);
        applyBaselineSqlDirectly(LEGACY_SCHEMA);
        seedLegacyRows(LEGACY_SCHEMA);

        var result = flywayFor(LEGACY_SCHEMA).migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(scalar("select count(*) from " + LEGACY_SCHEMA + ".users where email = 'legacy@example.com'"))
                .isEqualTo(1L);
        assertThat(scalar("select count(*) from " + LEGACY_SCHEMA + ".tasks where title = 'Legacy task'"))
                .isEqualTo(1L);
        // The leftover Alembic bookkeeping table is harmless and is deliberately left in place.
        assertThat(tableExists(LEGACY_SCHEMA, "alembic_version")).isTrue();
        assertThat(scalar("select count(*) from " + LEGACY_SCHEMA
                        + ".flyway_schema_history where type = 'BASELINE' and version = '1'"))
                .isEqualTo(1L);
    }

    @Test
    void adoptingAnExistingDatabaseTwiceStaysANoOp() throws SQLException {
        recreateSchema(LEGACY_SCHEMA);
        applyBaselineSqlDirectly(LEGACY_SCHEMA);
        seedLegacyRows(LEGACY_SCHEMA);

        flywayFor(LEGACY_SCHEMA).migrate();
        var second = flywayFor(LEGACY_SCHEMA).migrate();

        assertThat(second.migrationsExecuted).isZero();
        assertThat(scalar("select count(*) from " + LEGACY_SCHEMA + ".users")).isEqualTo(1L);
    }

    private Flyway flywayFor(String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private void recreateSchema(String schema) throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE", "CREATE SCHEMA " + schema);
    }

    /** Replays the schema the Alembic chain produced, without any Flyway bookkeeping. */
    private void applyBaselineSqlDirectly(String schema) throws SQLException {
        String sql = readBaselineSql();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            statement.execute(sql);
        }
    }

    private void seedLegacyRows(String schema) throws SQLException {
        execute(
                "INSERT INTO " + schema
                        + ".users (username, email, hashed_password, role) "
                        + "VALUES ('legacy', 'legacy@example.com', '$2b$12$abcdefghijklmnopqrstuv', 'user')",
                "INSERT INTO " + schema
                        + ".tasks (title, status, difficulty, creator_id) "
                        + "VALUES ('Legacy task', 'todo', 3, (select id from " + schema + ".users limit 1))",
                "CREATE TABLE " + schema + ".alembic_version (version_num varchar(32) NOT NULL PRIMARY KEY)",
                "INSERT INTO " + schema + ".alembic_version (version_num) VALUES ('c9d1e4f7a2b5')");
    }

    private String readBaselineSql() {
        try {
            return new String(
                    new ClassPathResource("db/migration/V1__baseline_schema.sql")
                            .getInputStream()
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void execute(String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        return scalar("select count(*) from information_schema.tables where table_schema = '" + schema
                        + "' and table_name = '" + table + "'")
                > 0;
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
