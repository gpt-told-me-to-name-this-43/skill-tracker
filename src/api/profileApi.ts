import { apiClient } from "./client";

export type Profile = {
  name: string;
  email: string;
  totalXp: number;
};

export type Skill = {
  id: number;
  name: string;
  level: number;
  experience: number;
};

export type ProfileProgress = {
  averageLevel: number;
};

export function getProfile(): Promise<Profile> {
  return apiClient.get<Profile>("/profile");
}

export function getSkills(): Promise<Skill[]> {
  return apiClient.get<Skill[]>("/profile/skills");
}

export function getProgress(): Promise<ProfileProgress> {
  return apiClient.get<ProfileProgress>("/profile/progress");
}
