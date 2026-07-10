export type TaskStatus = "todo" | "in_progress" | "review" | "done";

export type TaskAttachment = {
  id: number;
  name: string;
  url?: string;
};

export type RelatedIssue = {
  id: number;
  title: string;
};

export type Task = {
  id: number;
  title: string;
  description: string;
  status: TaskStatus;
  difficulty: number;
  deadline: string | null;
  creator_id: number;
  created_by?: string;
  assignee_id: number | null;
  assignee?: string;
  labels?: string[];
  attachments?: TaskAttachment[];
  related_issues?: RelatedIssue[];
  approved_by_id: number | null;
  approved_at: string | null;
  created_at: string;
  updated_at: string;
};
