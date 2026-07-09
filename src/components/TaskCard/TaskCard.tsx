import { Link } from "react-router-dom";
import type { Task } from "../../types/task";
import StatusBadge from "../StatusBadge/StatusBadge";
import "./TaskCard.css";

export default function TaskCard({ task }: { task: Task }) {
  return (
    <Link className="task-card" to={`/tasks/${task.id}`}>
      <article>
        <h2>{task.title}</h2>
        <StatusBadge status={task.status} />
        <p>Difficulty: {task.difficulty}/5</p>
        <p>Assignee: {task.assignee_id ?? "Unassigned"}</p>
        <p>Deadline: {task.deadline ?? "No deadline"}</p>
      </article>
    </Link>
  );
}
