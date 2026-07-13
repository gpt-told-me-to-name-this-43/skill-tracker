export type TaskStatus = "todo" | "in_progress" | "review" | "done";
export type MemberStatus = "active" | "away" | "inactive";

export type UserSummary = {
  id: number;
  username: string;
  avatar_url: string | null;
  position: string | null;
  member_status: MemberStatus;
  github_login: string | null;
  is_placeholder: boolean;
};

export type TeamSummary = {
  id: number;
  name: string;
};

export type Person = UserSummary & {
  role: string;
  team: TeamSummary | null;
};

export type Team = {
  id: number;
  name: string;
  description: string | null;
  member_count: number;
  lead: UserSummary | null;
  members: UserSummary[];
  created_at: string;
  updated_at: string;
};

export type Label = {
  id: number;
  name: string;
  color: string | null;
  created_at: string;
  updated_at: string;
};

export type TaskAttachment = {
  id: number;
  name: string;
  url: string;
  created_by: UserSummary;
  created_at: string;
};

export type Skill = {
  id: number;
  name: string;
  description: string | null;
};

export type TaskSkill = {
  skill: Skill;
  exp_reward: number;
};

export type RelatedTask = {
  id: number;
  title: string;
  status: TaskStatus;
};

export type TaskListItem = {
  id: number;
  title: string;
  status: TaskStatus;
  difficulty: number;
  deadline: string | null;
  creator: UserSummary;
  assignee: UserSummary | null;
  labels: Label[];
  attachments_count: number;
  related_tasks_count: number;
  github_issue_number: number | null;
  github_url: string | null;
  created_at: string;
  updated_at: string;
};

export type TaskDetail = TaskListItem & {
  description: string | null;
  attachments: TaskAttachment[];
  related_tasks: RelatedTask[];
  creator_id: number;
  assignee_id: number | null;
  approved_by_id: number | null;
  approved_at: string | null;
};

export type Task = TaskDetail;
