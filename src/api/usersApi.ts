import { apiClient } from "./client";

export type User = {
  id: number;
  name: string;
  email: string;
};

export function getUsers(): Promise<User[]> {
  return apiClient.get<User[]>("/users");
}
