import type { Task } from "../types/task";
import type { TaskStatus } from "../types/task";
import { apiClient } from "./client";

export type CreateTaskPayload = {
  title: string;
  description: string;
  deadline: string;
  difficulty: number;
  assignee: string;
};

export function getTasks(): Promise<Task[]> {
  return apiClient.get<Task[]>("/tasks");
}

export function getTaskById(taskId: number): Promise<Task | undefined> {
  return apiClient.get<Task>(`/tasks/${taskId}`);
}

export function createTask(task: CreateTaskPayload): Promise<Task> {
  return apiClient.post<Task>("/tasks", task);
}

export function updateTaskStatus(taskId: number, status: TaskStatus): Promise<Task | undefined> {
  return apiClient.patch<Task>(`/tasks/${taskId}/status`, { status });
}
