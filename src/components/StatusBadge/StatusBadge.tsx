import type { TaskStatus } from "../../types/task";
import { statusLabels } from "../../constants/taskStatus";
import "./StatusBadge.css";

type StatusBadgeProps = {
  status: TaskStatus;
};

export default function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`status-badge status-${status}`}>
      {statusLabels[status]}
    </span>
  );
}
