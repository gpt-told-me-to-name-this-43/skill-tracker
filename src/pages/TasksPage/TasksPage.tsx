import { Link } from "react-router-dom";
import { useState } from "react";

const tasks = [
  {
    id: 1,
    title: "Create Login Page",
    status: "In Progress",
    difficulty: 3,
    assignee: "John",
  },
];

const statuses = ["All", ...new Set(tasks.map((task) => task.status))];

export default function TasksPage() {
  const [status, setStatus] = useState("All");
  const filteredTasks = status === "All"
    ? tasks
    : tasks.filter((task) => task.status === status);

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
          {statuses.map((item) => (
            <option key={item} value={item}>{item}</option>
          ))}
        </select>
      </section>

      <section className="cards-list">
        {filteredTasks.map((task) => (
          <article className="task-card" key={task.id}>
            <h2>{task.title}</h2>
            <p>Assignee: {task.assignee}</p>
            <p>Difficulty: {task.difficulty}/5</p>
            <span className="status-badge">{task.status}</span>
            <Link className="page-link" to={`/tasks/${task.id}`}>Open details</Link>
          </article>
        ))}
      </section>
    </main>
  );
}
