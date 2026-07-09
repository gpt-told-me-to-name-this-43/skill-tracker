export type TaskStatus = "todo" | "in_progress" | "review" | "done";

export type Task = {
  id: number;
  title: string;
  description: string;
  status: TaskStatus;
  difficulty: number;
  deadline: string | null;
  creator_id: number;
  assignee_id: number | null;
  approved_by_id: number | null;
  approved_at: string | null;
  created_at: string;
  updated_at: string;
};
