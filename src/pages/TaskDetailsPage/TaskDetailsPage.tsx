import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getTaskById, updateTaskStatus } from "../../api/tasksApi";
import StatusBadge from "../../components/StatusBadge/StatusBadge";
import { statusLabels } from "../../constants/taskStatus";
import type { Task, TaskStatus } from "../../types/task";

const statuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];

export default function TaskDetailsPage() {
  const { taskId } = useParams();
  const [task, setTask] = useState<Task | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [statusError, setStatusError] = useState("");

  async function handleStatusChange(status: TaskStatus) {
    if (!task) {
      return;
    }

    setStatusError("");

    try {
      const updatedTask = await updateTaskStatus(task.id, status);

      if (!updatedTask) {
        setStatusError("Не удалось обновить статус задачи.");
        return;
      }

      setTask({ ...updatedTask });
    } catch {
      setStatusError("Не удалось обновить статус задачи.");
    }
  }

  useEffect(() => {
    async function loadTask() {
      const id = Number(taskId);

      if (!id) {
        setError("Некорректный id задачи.");
        setLoading(false);
        return;
      }

      try {
        const data = await getTaskById(id);

        if (!data) {
          setError("Задача не найдена.");
          return;
        }

        setTask(data);
      } catch {
        setError("Не удалось загрузить задачу.");
      } finally {
        setLoading(false);
      }
    }

    loadTask();
  }, [taskId]);

  if (loading) {
    return (
      <main className="page-shell">
        <section className="page-panel">Загрузка задачи...</section>
      </main>
    );
  }

  if (error || !task) {
    return (
      <main className="page-shell">
        <section className="page-panel state-error">{error}</section>
        <Link className="page-link" to="/tasks">Назад к задачам</Link>
      </main>
    );
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <p>Task #{taskId}</p>
        <h1>{task.title}</h1>
      </header>

      <section className="page-panel">
        <p>{task.description}</p>
        <dl className="details-list">
          <dt>Assignee</dt>
          <dd>{task.assignee}</dd>
          <dt>Status</dt>
          <dd><StatusBadge status={task.status} /></dd>
          <dt>Difficulty</dt>
          <dd>{task.difficulty}/5</dd>
          <dt>Deadline</dt>
          <dd>{task.deadline}</dd>
        </dl>
        <section className="actions-row">
          {statuses.map((status) => (
            <button disabled={task.status === status} key={status} onClick={() => handleStatusChange(status)} type="button">
              {statusLabels[status]}
            </button>
          ))}
        </section>
        {statusError && <p className="state-error">{statusError}</p>}
        <Link className="page-link" to="/tasks">Назад к задачам</Link>
      </section>
    </main>
  );
}
