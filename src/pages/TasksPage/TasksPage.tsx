import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { getTasks } from "../../api/tasksApi";
import TaskCard from "../../components/TaskCard/TaskCard";
import { statusLabels } from "../../constants/taskStatus";
import type { Task } from "../../types/task";

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [status, setStatus] = useState("all");
  const [assignee, setAssignee] = useState("all");
  const [difficulty, setDifficulty] = useState("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadTasks() {
      try {
        const data = await getTasks();
        setTasks(data);
      } catch {
        setError("Не удалось загрузить задачи.");
      } finally {
        setLoading(false);
      }
    }

    loadTasks();
  }, []);

  const statuses = [...new Set(tasks.map((task) => task.status))];
  const assignees = [...new Set(tasks.map((task) => task.assignee))];
  const difficulties = [...new Set(tasks.map((task) => task.difficulty))];

  const filteredTasks = tasks.filter((task) => {
    const byStatus = status === "all" || task.status === status;
    const byAssignee = assignee === "all" || task.assignee === assignee;
    const byDifficulty = difficulty === "all" || task.difficulty === Number(difficulty);

    return byStatus && byAssignee && byDifficulty;
  });

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Tasks</p>
          <h1>Task list</h1>
        </section>
        <Link className="button-link" to="/tasks/new">Create Task</Link>
      </header>

      <section className="toolbar">
        <label htmlFor="status">Status</label>
        <select id="status" value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All</option>
          {statuses.map((item) => (
            <option key={item} value={item}>{statusLabels[item]}</option>
          ))}
        </select>

        <label htmlFor="assignee">Assignee</label>
        <select id="assignee" value={assignee} onChange={(event) => setAssignee(event.target.value)}>
          <option value="all">All</option>
          {assignees.map((item) => (
            <option key={item} value={item}>{item}</option>
          ))}
        </select>

        <label htmlFor="difficulty">Difficulty</label>
        <select id="difficulty" value={difficulty} onChange={(event) => setDifficulty(event.target.value)}>
          <option value="all">All</option>
          {difficulties.map((item) => (
            <option key={item} value={item}>{item}/5</option>
          ))}
        </select>
      </section>

      {loading && <section className="page-panel">Загрузка задач...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && tasks.length === 0 && (
        <section className="page-panel">Пока нет задач.</section>
      )}
      {!loading && !error && tasks.length > 0 && filteredTasks.length === 0 && (
        <section className="page-panel">Задачи не найдены.</section>
      )}
      {!loading && !error && filteredTasks.length > 0 && (
        <section className="cards-list">
          {filteredTasks.map((task) => (
            <TaskCard key={task.id} task={task} />
          ))}
        </section>
      )}
    </main>
  );
}
