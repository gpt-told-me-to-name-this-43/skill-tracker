package com.skilltracker.controller;

import com.skilltracker.domain.User;
import com.skilltracker.dto.GitHubSyncResponse;
import com.skilltracker.security.CurrentUser;
import com.skilltracker.service.GitHubImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final GitHubImportService gitHubImportService;

    public IntegrationController(GitHubImportService gitHubImportService) {
        this.gitHubImportService = gitHubImportService;
    }

    /** Imports issues from the configured GitHub repository; one-way and idempotent. */
    @PostMapping("/github/sync")
    public GitHubSyncResponse syncGithubIssues(@CurrentUser User currentUser) {
        return gitHubImportService.sync(currentUser.getId());
    }
}
