package com.skilltracker.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mirrors the JWT_SECRET / JWT_ALGORITHM / ACCESS_TOKEN_TTL_MINUTES environment contract. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, String algorithm, long accessTokenTtlMinutes) {

    /** RFC 7518 requires an HMAC key at least as long as the hash output. */
    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long for " + algorithm + " signatures");
        }
    }
}
