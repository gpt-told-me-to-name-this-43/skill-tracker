import type { MemberStatus, Person, Team } from "../types/task";
import { apiClient } from "./client";

export type User = Person;

export type UsersQuery = {
  team_id?: number | null;
  member_status?: MemberStatus | "all";
  limit?: number;
  offset?: number;
};

export type WorkspaceProfilePayload = {
  avatar_url?: string | null;
  position?: string | null;
  member_status?: MemberStatus;
};

export type TeamPayload = {
  name: string;
  description?: string | null;
};

export type TeamMembersPayload = {
  user_ids: number[];
  lead_id: number | null;
};

function toQuery(params: Record<string, string | number | undefined | null>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "all") {
      search.set(key, String(value));
    }
  });
  const query = search.toString();
  return query ? `?${query}` : "";
}

export function getUsers(query: UsersQuery = {}): Promise<Person[]> {
  return apiClient.get<Person[]>(`/users${toQuery(query)}`);
}

export function updateWorkspaceProfile(
  userId: number,
  payload: WorkspaceProfilePayload,
): Promise<Person> {
  return apiClient.patch<Person>(`/users/${userId}/workspace-profile`, payload);
}

export function getTeams(): Promise<Team[]> {
  return apiClient.get<Team[]>("/teams");
}

export function getTeamById(teamId: number): Promise<Team> {
  return apiClient.get<Team>(`/teams/${teamId}`);
}

export function createTeam(payload: TeamPayload): Promise<Team> {
  return apiClient.post<Team>("/teams", payload);
}

export function updateTeam(teamId: number, payload: Partial<TeamPayload>): Promise<Team> {
  return apiClient.patch<Team>(`/teams/${teamId}`, payload);
}

export function setTeamMembers(teamId: number, payload: TeamMembersPayload): Promise<Team> {
  return apiClient.put<Team>(`/teams/${teamId}/members`, payload);
}
