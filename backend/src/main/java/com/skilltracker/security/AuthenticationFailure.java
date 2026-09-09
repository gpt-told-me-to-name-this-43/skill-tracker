package com.skilltracker.security;

/** Why the bearer credentials were rejected, so the 401 body can keep its original wording. */
public enum AuthenticationFailure {
    MISSING("Not authenticated"),
    INVALID("Invalid token");

    public static final String REQUEST_ATTRIBUTE = AuthenticationFailure.class.getName();

    private final String message;

    AuthenticationFailure(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
