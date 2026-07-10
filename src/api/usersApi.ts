import { apiClient } from "./client";

export type User = {
  id: number;
  email: string;
  username: string;
  role: string;
  avatar_url?: string | null;
  team?: string | null;
  status?: string | null;
  created_at: string;
};

export function getUsers(): Promise<User[]> {
  return apiClient.get<User[]>("/users");
}
