import { Link, useParams } from "react-router-dom";

const task = {
  title: "Create Login Page",
  description: "Build a responsive login page with email, password, background and submit button.",
  assignee: "John",
  status: "In Progress",
  difficulty: 3,
  deadline: "2026-07-03",
};

export default function TaskDetailsPage() {
  const { taskId } = useParams();

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
          <dd>{task.status}</dd>
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
