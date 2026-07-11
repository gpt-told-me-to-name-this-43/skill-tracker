import { CSS } from "@dnd-kit/utilities";
import { Link, useNavigate } from "react-router-dom";
import type { HTMLAttributes, MouseEvent } from "react";
import type { Transform } from "@dnd-kit/utilities";
import type { TaskListItem } from "../../types/task";
import { getDeadlineParts } from "../../utils/dateTime";
import StatusBadge from "../StatusBadge/StatusBadge";
import "./TaskCard.css";

type TaskCardProps = {
  task: TaskListItem;
  attributes?: HTMLAttributes<HTMLElement>;
  listeners?: HTMLAttributes<HTMLElement>;
  setNodeRef?: (node: HTMLElement | null) => void;
  transform?: Transform | null;
  transition?: string;
  isDragging?: boolean;
};

function getInitials(name: string) {
  return name
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "U";
}

function isOverdue(deadline: string | null, status: TaskListItem["status"]) {
  if (!deadline || status === "done") {
    return false;
  }

  return new Date(deadline).getTime() < new Date().setHours(0, 0, 0, 0);
}

export default function TaskCard({
  task,
  attributes,
  listeners,
  setNodeRef,
  transform,
  transition,
  isDragging = false,
}: TaskCardProps) {
  const navigate = useNavigate();
  const style = {
    transform: CSS.Transform.toString(transform ?? null),
    transition,
  };
  const overdue = isOverdue(task.deadline, task.status);
  const deadline = getDeadlineParts(task.deadline, true);

  function handleCardClick(event: MouseEvent<HTMLElement>) {
    if (isDragging || event.defaultPrevented) {
      return;
    }

    const target = event.target as HTMLElement;
    if (target.closest("a, button, input, select, textarea")) {
      return;
    }

    navigate(`/tasks/${task.id}`);
  }

  return (
    <article
      className={`task-card ${isDragging ? "is-dragging" : ""}`}
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      onClick={handleCardClick}
    >
      <header className="task-card-header">
        <h2>{task.title}</h2>
        <StatusBadge status={task.status} />
      </header>

      {task.labels.length > 0 && (
        <ul className="label-list">
          {task.labels.map((label) => (
            <li key={label.id} style={label.color ? { borderColor: label.color } : undefined}>
              {label.name}
            </li>
          ))}
        </ul>
      )}

      <section className="task-card-people">
        <span className="avatar-fallback">
          {task.assignee?.avatar_url ? (
            <img alt="" src={task.assignee.avatar_url} />
          ) : (
            getInitials(task.assignee?.username ?? "Unassigned")
          )}
        </span>
        <div>
          <small>Assignee</small>
          <strong>{task.assignee?.username ?? "Unassigned"}</strong>
        </div>
      </section>

      <section className="task-card-meta">
        <span>{task.difficulty}/5 difficulty</span>
        <div className={`task-card-due ${overdue ? "is-overdue" : ""}`}>
          <small>Deadline</small>
          <span className="task-card-due-values">
            <strong>{deadline.date}</strong>
            {deadline.time && <em>{deadline.time}</em>}
          </span>
        </div>
      </section>

      <footer className="task-card-footer">
        <span>{task.attachments_count} attachments</span>
        <span>{task.related_tasks_count} related</span>
        <Link
          className="task-card-open"
          onPointerDown={(event) => event.stopPropagation()}
          to={`/tasks/${task.id}`}
        >
          Open details
        </Link>
      </footer>
    </article>
  );
}
