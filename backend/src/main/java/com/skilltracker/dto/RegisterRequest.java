package com.skilltracker.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotNull @Email String email,
        @NotNull @Size(min = 3) String username,
        @NotNull @Size(min = 8) String password) {

    /** bcrypt only hashes the first 72 bytes, so longer passwords must not be silently truncated. */
    public static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    @AssertTrue(message = "password must be at most 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_PASSWORD_BYTES;
    }
}
