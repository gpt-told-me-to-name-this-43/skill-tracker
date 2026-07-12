import type {
  Label,
  RelatedTask,
  Skill,
  TaskAttachment,
  TaskDetail,
  TaskListItem,
  TaskSkill,
  TaskStatus,
} from "../types/task";
import { apiClient } from "./client";

export type CreateTaskPayload = {
  title: string;
  description: string;
  deadline: string;
  difficulty: number;
  assignee_id: number | null;
};

export type UpdateTaskPayload = {
  title?: string;
  description?: string | null;
  deadline?: string | null;
  difficulty?: number;
};

export type CreateLabelPayload = {
  name: string;
  color: string | null;
};

export type CreateAttachmentPayload = {
  name: string;
  url: string;
};

export function getTasks(): Promise<TaskListItem[]> {
  return apiClient.get<TaskListItem[]>("/tasks");
}

export function getTaskById(taskId: number): Promise<TaskDetail | undefined> {
  return apiClient.get<TaskDetail>(`/tasks/${taskId}`);
}

export function createTask(task: CreateTaskPayload): Promise<TaskDetail> {
  return apiClient.post<TaskDetail>("/tasks", task);
}

export function updateTask(taskId: number, task: UpdateTaskPayload): Promise<TaskDetail> {
  return apiClient.patch<TaskDetail>(`/tasks/${taskId}`, task);
}

export function updateTaskStatus(taskId: number, status: TaskStatus): Promise<TaskDetail | undefined> {
  return apiClient.patch<TaskDetail>(`/tasks/${taskId}/status`, { status });
}

export function assignTask(taskId: number, assigneeId: number | null): Promise<TaskDetail> {
  return apiClient.patch<TaskDetail>(`/tasks/${taskId}/assign`, { assignee_id: assigneeId });
}

export function approveTask(taskId: number): Promise<TaskDetail | undefined> {
  return apiClient.patch<TaskDetail>(`/tasks/${taskId}/approve`);
}

export function getLabels(): Promise<Label[]> {
  return apiClient.get<Label[]>("/labels");
}

export function getSkills(): Promise<Skill[]> {
  return apiClient.get<Skill[]>("/skills");
}

export function createLabel(label: CreateLabelPayload): Promise<Label> {
  return apiClient.post<Label>("/labels", label);
}

export function setTaskLabels(taskId: number, labelIds: number[]): Promise<TaskDetail> {
  return apiClient.put<TaskDetail>(`/tasks/${taskId}/labels`, { label_ids: labelIds });
}

export function createTaskAttachment(
  taskId: number,
  attachment: CreateAttachmentPayload,
): Promise<TaskAttachment> {
  return apiClient.post<TaskAttachment>(`/tasks/${taskId}/attachments`, attachment);
}

export function uploadTaskAttachment(taskId: number, file: File): Promise<TaskAttachment> {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient.post<TaskAttachment>(`/tasks/${taskId}/attachments/upload`, formData);
}

export function deleteTaskAttachment(taskId: number, attachmentId: number): Promise<void> {
  return apiClient.delete<void>(`/tasks/${taskId}/attachments/${attachmentId}`);
}

export function setRelatedTasks(taskId: number, taskIds: number[]): Promise<RelatedTask[]> {
  return apiClient.put<RelatedTask[]>(`/tasks/${taskId}/related`, { task_ids: taskIds });
}

export function getTaskSkills(taskId: number): Promise<TaskSkill[]> {
  return apiClient.get<TaskSkill[]>(`/tasks/${taskId}/skills`);
}

export function setTaskSkills(
  taskId: number,
  skills: { skill_id: number; exp_reward: number }[],
): Promise<TaskSkill[]> {
  return apiClient.put<TaskSkill[]>(`/tasks/${taskId}/skills`, { skills });
}
