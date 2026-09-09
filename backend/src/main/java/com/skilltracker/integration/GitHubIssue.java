package com.skilltracker.integration;

import java.util.List;

public record GitHubIssue(
        int number, String title, String body, String state, String assigneeLogin, List<String> labels) {}
