import { apiClient } from "./client";

export type GitHubSyncResult = {
  created: number;
  updated: number;
  users_created: number;
};

export const GITHUB_SYNC_COMPLETED_EVENT = "github-sync-completed";

export function syncGithubIssues(): Promise<GitHubSyncResult> {
  return apiClient.post<GitHubSyncResult>("/integrations/github/sync");
}
