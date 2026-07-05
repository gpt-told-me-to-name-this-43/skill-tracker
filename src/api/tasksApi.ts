import type { Task } from "../types/task";

const mockTasks: Task[] = [
  {
    id: 1,
    title: "Create Login Page",
    description: "Build a responsive login page with email, password, background and submit button.",
    status: "in_progress",
    difficulty: 3,
    deadline: "2026-07-08",
    assignee: "John",
    createdAt: "2026-07-03",
    updatedAt: "2026-07-05",
  },
  {
    id: 2,
    title: "Add Task Filters",
    description: "Add client-side filters by status, assignee and difficulty.",
    status: "todo",
    difficulty: 2,
    deadline: "2026-07-09",
    assignee: "Anna",
    createdAt: "2026-07-04",
    updatedAt: "2026-07-04",
  },
  {
    id: 3,
    title: "Review Auth Context",
    description: "Check mock auth flow and protected routes before backend integration.",
    status: "review",
    difficulty: 4,
    deadline: "2026-07-10",
    assignee: "John",
    createdAt: "2026-07-04",
    updatedAt: "2026-07-05",
  },
  {
    id: 4,
    title: "Prepare Profile Mock",
    description: "Show user progress, total XP and skill levels on profile page.",
    status: "done",
    difficulty: 2,
    deadline: "2026-07-06",
    assignee: "Kate",
    createdAt: "2026-07-02",
    updatedAt: "2026-07-05",
  },
];

export function getTasks(): Promise<Task[]> {
  return Promise.resolve(mockTasks);
}

export function getTaskById(taskId: number): Promise<Task | undefined> {
  return Promise.resolve(mockTasks.find((task) => task.id === taskId));
}
