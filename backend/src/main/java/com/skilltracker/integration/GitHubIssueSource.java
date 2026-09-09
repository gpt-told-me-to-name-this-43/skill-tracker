package com.skilltracker.integration;

import java.util.List;

/** Read side of the GitHub integration, kept behind an interface so the import can be tested. */
public interface GitHubIssueSource {

    List<GitHubIssue> fetchIssues();
}
