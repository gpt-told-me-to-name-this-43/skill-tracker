package com.skilltracker.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.skilltracker.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the same HS256 tokens the FastAPI backend used: a {@code sub} claim holding
 * the user id and an {@code exp} claim, signed with the raw {@code JWT_SECRET} bytes.
 */
@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwsAlgorithm algorithm;
    private final Duration tokenTtl;

    public JwtService(JwtProperties properties) {
        MacAlgorithm macAlgorithm = MacAlgorithm.from(properties.algorithm());
        if (macAlgorithm == null) {
            throw new IllegalStateException("Unsupported JWT_ALGORITHM: " + properties.algorithm());
        }
        this.algorithm = macAlgorithm;
        this.tokenTtl = Duration.ofMinutes(properties.accessTokenTtlMinutes());

        SecretKeySpec key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));

        NimbusJwtDecoder nimbusDecoder =
                NimbusJwtDecoder.withSecretKey(key).macAlgorithm(macAlgorithm).build();
        // PyJWT applied no leeway when checking exp; keep expiry strict for parity.
        nimbusDecoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        this.decoder = nimbusDecoder;
    }

    public String createAccessToken(Integer userId) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .expiresAt(issuedAt.plus(tokenTtl))
                .build();
        JwsHeader header = JwsHeader.with(algorithm).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Returns the user id carried by the token, or empty when it is invalid or expired. */
    public Integer parseSubject(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String subject = jwt.getSubject();
            return subject == null ? null : Integer.valueOf(subject);
        } catch (JwtException | NumberFormatException invalid) {
            return null;
        }
    }
}
