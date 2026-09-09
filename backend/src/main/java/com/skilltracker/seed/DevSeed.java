package com.skilltracker.seed;

import java.util.List;

/** The fixtures a demo environment needs: one admin login and the core skill catalogue. */
public final class DevSeed {

    public record DevUser(String email, String username, String password, String role) {}

    /** An admin so the demo can exercise the whole feature set, including member editing. */
    public static final List<DevUser> USERS = List.of(new DevUser("test@example.com", "test", "password123", "admin"));

    public static final List<String[]> SKILLS = List.of(
            new String[] {"backend", "Server-side development"},
            new String[] {"frontend", "Client-side development"},
            new String[] {"database", "Data modeling and queries"},
            new String[] {"api_design", "API contracts and design"},
            new String[] {"devops", "Infrastructure and delivery"},
            new String[] {"testing", "Automated and manual testing"},
            new String[] {"documentation", "Writing and maintaining docs"},
            new String[] {"debugging", "Investigating and fixing defects"});

    private DevSeed() {}
}
