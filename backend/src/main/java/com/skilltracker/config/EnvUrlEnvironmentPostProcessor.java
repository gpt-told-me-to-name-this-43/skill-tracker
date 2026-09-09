package com.skilltracker.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps the developer-facing {@code DATABASE_URL} / {@code REDIS_URL} environment interface the
 * FastAPI backend used and translates it into the Spring datasource and Redis properties.
 *
 * <p>The SQLAlchemy driver suffix ({@code postgresql+asyncpg://}) is accepted and ignored so
 * existing {@code .env} files and Compose overrides keep working unchanged.
 */
public class EnvUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "skillTrackerLegacyUrls";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();
        applyDatabaseUrl(environment.getProperty("DATABASE_URL"), properties);
        applyRedisUrl(environment.getProperty("REDIS_URL"), properties);

        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    private void applyDatabaseUrl(String databaseUrl, Map<String, Object> properties) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (databaseUrl.startsWith("jdbc:")) {
            properties.put("spring.datasource.url", databaseUrl);
            return;
        }

        URI uri = URI.create(databaseUrl);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        properties.put(
                "spring.datasource.url", "jdbc:postgresql://" + hostAndPort(uri, 5432) + pathOrEmpty(uri) + query);
        applyUserInfo(uri, properties, "spring.datasource.username", "spring.datasource.password");
    }

    private void applyRedisUrl(String redisUrl, Map<String, Object> properties) {
        if (redisUrl == null || redisUrl.isBlank()) {
            return;
        }

        URI uri = URI.create(redisUrl);
        properties.put("spring.data.redis.host", uri.getHost());
        properties.put("spring.data.redis.port", uri.getPort() == -1 ? 6379 : uri.getPort());
        properties.put("spring.data.redis.ssl.enabled", "rediss".equalsIgnoreCase(uri.getScheme()));

        String database = pathOrEmpty(uri).replace("/", "");
        if (!database.isEmpty()) {
            properties.put("spring.data.redis.database", database);
        }
        applyUserInfo(uri, properties, "spring.data.redis.username", "spring.data.redis.password");
    }

    private void applyUserInfo(URI uri, Map<String, Object> properties, String userKey, String passwordKey) {
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null || userInfo.isEmpty()) {
            return;
        }

        int separator = userInfo.indexOf(':');
        String user = separator == -1 ? userInfo : userInfo.substring(0, separator);
        if (!user.isEmpty()) {
            properties.put(userKey, decode(user));
        }
        if (separator != -1) {
            properties.put(passwordKey, decode(userInfo.substring(separator + 1)));
        }
    }

    private String hostAndPort(URI uri, int defaultPort) {
        return uri.getHost() + ":" + (uri.getPort() == -1 ? defaultPort : uri.getPort());
    }

    private String pathOrEmpty(URI uri) {
        return uri.getPath() == null ? "" : uri.getPath();
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
