package com.skilltracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mirrors the GITHUB_REPO / GITHUB_TOKEN / GITHUB_API_URL environment contract. */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String repo, String apiUrl, String token) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
