import { Link } from "react-router-dom";
import type { DragEvent } from "react";
import type { Task } from "../../types/task";
import StatusBadge from "../StatusBadge/StatusBadge";
import "./TaskCard.css";

type TaskCardProps = {
  task: Task;
  assigneeName?: string;
  creatorName?: string;
  onDragStart?: (event: DragEvent<HTMLAnchorElement>, task: Task) => void;
};

export default function TaskCard({
  task,
  assigneeName,
  creatorName,
  onDragStart,
}: TaskCardProps) {
  const labels = task.labels ?? [];
  const attachmentsCount = task.attachments?.length ?? 0;
  const relatedIssuesCount = task.related_issues?.length ?? 0;

  return (
    <Link
      className="task-card"
      draggable={Boolean(onDragStart)}
      onDragStart={(event) => onDragStart?.(event, task)}
      to={`/tasks/${task.id}`}
    >
      <article>
        <header className="task-card-header">
          <h2>{task.title}</h2>
          <StatusBadge status={task.status} />
        </header>

        <p>{task.description}</p>

        {labels.length > 0 && (
          <ul className="label-list">
            {labels.map((label) => (
              <li key={label}>{label}</li>
            ))}
          </ul>
        )}

        <dl className="task-card-meta">
          <div>
            <dt>Created by</dt>
            <dd>{task.created_by ?? creatorName ?? `#${task.creator_id}`}</dd>
          </div>
          <div>
            <dt>Assignee</dt>
            <dd>{task.assignee ?? assigneeName ?? task.assignee_id ?? "Unassigned"}</dd>
          </div>
          <div>
            <dt>Difficulty</dt>
            <dd>{task.difficulty}/5</dd>
          </div>
          <div>
            <dt>Deadline</dt>
            <dd>{task.deadline ?? "No deadline"}</dd>
          </div>
        </dl>

        <footer className="task-card-footer">
          <span>{attachmentsCount} attachments</span>
          <span>{relatedIssuesCount} related</span>
        </footer>
      </article>
    </Link>
  );
}
