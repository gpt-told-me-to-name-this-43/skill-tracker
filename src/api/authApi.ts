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

export type LoginResponse = {
  access_token: string;
  token_type: "bearer";
};

export function login(email: string, password: string): Promise<LoginResponse> {
  return apiClient.post<LoginResponse>("/auth/login", { email, password });
}

export function getCurrentUser(): Promise<User> {
  return apiClient.get<User>("/auth/me");
}
