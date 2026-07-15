import { apiClient } from "./client";

export type Skill = {
  id: number;
  name: string;
  description: string | null;
};

export type UserSkill = {
  skill: Skill;
  level: number;
  experience: number;
  current_level_xp: number;
  next_level_xp: number;
  progress_to_next_level: number;
};

export type ProfileProgress = {
  user_id: number;
  total_experience: number;
  skills_count: number;
  average_level: number;
  skills: UserSkill[];
};

export function getUserSkills(userId: number): Promise<UserSkill[]> {
  return apiClient.get<UserSkill[]>(`/users/${userId}/skills`);
}

export function getUserProgress(userId: number): Promise<ProfileProgress> {
  return apiClient.get<ProfileProgress>(`/users/${userId}/progress`);
}
