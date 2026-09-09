package com.skilltracker.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Workspace presence of a user. Stored as a plain varchar, exactly as the FastAPI backend did. */
public enum MemberStatus {
    ACTIVE("active"),
    AWAY("away"),
    INACTIVE("inactive");

    private final String value;

    MemberStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MemberStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown member status: " + value));
    }
}
