export type TaskStatus = "todo" | "in_progress" | "review" | "done";

export type Task = {
  id: number;
  title: string;
  description: string;
  status: TaskStatus;
  difficulty: number;
  deadline: string;
  assignee: string;
  createdAt: string;
  updatedAt: string;
};
