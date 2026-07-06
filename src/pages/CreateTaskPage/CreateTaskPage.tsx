import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { createTask } from "../../api/tasksApi";
import { getUsers, type User } from "../../api/usersApi";

export default function CreateTaskPage() {
  const navigate = useNavigate();
  const [users, setUsers] = useState<User[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [deadline, setDeadline] = useState("");
  const [difficulty, setDifficulty] = useState("3");
  const [assignee, setAssignee] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadUsers() {
      try {
        const data = await getUsers();
        setUsers(data);
        setAssignee(data[0]?.name ?? "");
      } catch {
        setError("Не удалось загрузить пользователей.");
      } finally {
        setLoading(false);
      }
    }

    loadUsers();
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const task = await createTask({
        title,
        description,
        deadline,
        difficulty: Number(difficulty),
        assignee,
      });

      console.log("Created task", task);
      navigate("/tasks");
    } catch {
      setError("Не удалось создать задачу.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <p>Create Task</p>
        <h1>New task</h1>
      </header>

      {loading && <section className="page-panel">Загрузка пользователей...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && users.length === 0 && (
        <section className="page-panel">Нет пользователей для назначения задачи.</section>
      )}
      {!loading && users.length > 0 && (
      <form className="page-panel task-form" onSubmit={handleSubmit}>
        <label htmlFor="title">Title</label>
        <input id="title" name="title" onChange={(event) => setTitle(event.target.value)} placeholder="Create Login Page" required value={title} />

        <label htmlFor="description">Description</label>
        <textarea id="description" name="description" onChange={(event) => setDescription(event.target.value)} placeholder="Describe what should be done" required value={description} />

        <label htmlFor="deadline">Deadline</label>
        <input id="deadline" name="deadline" onChange={(event) => setDeadline(event.target.value)} required type="date" value={deadline} />

        <label htmlFor="difficulty">Difficulty</label>
        <select id="difficulty" name="difficulty" onChange={(event) => setDifficulty(event.target.value)} value={difficulty}>
          <option value="1">1 - Easy</option>
          <option value="2">2 - Normal</option>
          <option value="3">3 - Medium</option>
          <option value="4">4 - Hard</option>
          <option value="5">5 - Expert</option>
        </select>

        <label htmlFor="assignee">Assignee</label>
        <select id="assignee" name="assignee" onChange={(event) => setAssignee(event.target.value)} required value={assignee}>
          {users.map((user) => (
            <option key={user.id} value={user.name}>{user.name}</option>
          ))}
        </select>

        <button className="submit-button" disabled={submitting} type="submit">
          {submitting ? "Creating..." : "Create task"}
        </button>
      </form>
      )}
    </main>
  );
}
