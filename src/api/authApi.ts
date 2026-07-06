import { apiClient } from "./client";

export type User = {
  id: number;
  name: string;
  email: string;
};

export type LoginResponse = {
  token: string;
  user: User;
};

export function login(email: string, password: string): Promise<LoginResponse> {
  return apiClient.post<LoginResponse>("/auth/login", { email, password });
}

export function logout(): Promise<void> {
  return apiClient.post<void>("/auth/logout");
}

export function getCurrentUser(): Promise<User> {
  return apiClient.get<User>("/auth/me");
}
