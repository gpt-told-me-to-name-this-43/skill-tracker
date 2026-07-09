import { apiClient } from "./client";

export type User = {
  id: number;
  email: string;
  username: string;
  role: string;
  created_at: string;
};

export function getUsers(): Promise<User[]> {
  return apiClient.get<User[]>("/users");
}
