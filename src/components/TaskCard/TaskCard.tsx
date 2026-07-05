import { Link } from "react-router-dom";
import type { Task } from "../../types/task";
import "./TaskCard.css";

const statusLabels = {
  todo: "To Do",
  in_progress: "In Progress",
  review: "Review",
  done: "Done",
};

export default function TaskCard({ task }: { task: Task }) {
  return (
    <Link className="task-card" to={`/tasks/${task.id}`}>
      <article>
        <h2>{task.title}</h2>
        <span className={`status-badge status-${task.status}`}>
          {statusLabels[task.status]}
        </span>
        <p>Difficulty: {task.difficulty}/5</p>
        <p>Assignee: {task.assignee}</p>
        <p>Deadline: {task.deadline}</p>
      </article>
    </Link>
  );
}
