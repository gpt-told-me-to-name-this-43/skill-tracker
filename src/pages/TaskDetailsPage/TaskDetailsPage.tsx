import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getTaskById } from "../../api/tasksApi";
import type { Task, TaskStatus } from "../../types/task";

const statusLabels: Record<TaskStatus, string> = {
  todo: "To Do",
  in_progress: "In Progress",
  review: "Review",
  done: "Done",
};

export default function TaskDetailsPage() {
  const { taskId } = useParams();
  const [task, setTask] = useState<Task | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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
          <dd>{statusLabels[task.status]}</dd>
          <dt>Difficulty</dt>
          <dd>{task.difficulty}/5</dd>
          <dt>Deadline</dt>
          <dd>{task.deadline}</dd>
        </dl>
        <section className="actions-row">
          <button type="button">To Do</button>
          <button type="button">In Progress</button>
          <button type="button">Done</button>
        </section>
        <Link className="page-link" to="/tasks">Назад к задачам</Link>
      </section>
    </main>
  );
}
