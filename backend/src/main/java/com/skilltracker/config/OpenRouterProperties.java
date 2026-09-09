package com.skilltracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mirrors the OPENROUTER_* environment contract; the API key stays optional. */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(String apiKey, String model, String apiUrl) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
