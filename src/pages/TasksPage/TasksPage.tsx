import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import type { DragEvent } from "react";
import { getTasks, updateTaskStatus } from "../../api/tasksApi";
import { getUsers, type User } from "../../api/usersApi";
import TaskCard from "../../components/TaskCard/TaskCard";
import { statusLabels } from "../../constants/taskStatus";
import type { Task, TaskStatus } from "../../types/task";

const kanbanStatuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];

function getUserName(users: User[], userId: number | null) {
  if (userId === null) {
    return undefined;
  }

  return users.find((user) => user.id === userId)?.username;
}

function getInitials(user: User) {
  return user.username
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "U";
}

function getTeamName(user: User) {
  return user.team || "Project Team";
}

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [status, setStatus] = useState("all");
  const [assignee, setAssignee] = useState("all");
  const [difficulty, setDifficulty] = useState("all");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [boardError, setBoardError] = useState("");

  useEffect(() => {
    async function loadProject() {
      try {
        const [tasksData, usersData] = await Promise.all([
          getTasks(),
          getUsers(),
        ]);

        setTasks(tasksData);
        setUsers(usersData);
      } catch {
        setError("Не удалось загрузить проект.");
      } finally {
        setLoading(false);
      }
    }

    loadProject();
  }, []);

  const assignees = [...new Set(tasks.map((task) => task.assignee_id))]
    .sort((first, second) => Number(first ?? 0) - Number(second ?? 0));
  const difficulties = [...new Set(tasks.map((task) => task.difficulty))];
  const teams = users.reduce<Record<string, User[]>>((acc, user) => {
    const team = getTeamName(user);
    acc[team] = [...(acc[team] ?? []), user];
    return acc;
  }, {});

  const filteredTasks = tasks.filter((task) => {
    const byStatus = status === "all" || task.status === status;
    const byAssignee = assignee === "all" || String(task.assignee_id) === assignee;
    const byDifficulty = difficulty === "all" || task.difficulty === Number(difficulty);

    return byStatus && byAssignee && byDifficulty;
  });

  function handleDragStart(event: DragEvent<HTMLAnchorElement>, task: Task) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", String(task.id));
  }

  function handleDragOver(event: DragEvent<HTMLElement>) {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
  }

  async function handleDrop(event: DragEvent<HTMLElement>, nextStatus: TaskStatus) {
    event.preventDefault();
    setBoardError("");

    const taskId = Number(event.dataTransfer.getData("text/plain"));
    const task = tasks.find((item) => item.id === taskId);

    if (!task || task.status === nextStatus) {
      return;
    }

    const previousTasks = tasks;
    setTasks((currentTasks) => currentTasks.map((item) => (
      item.id === taskId ? { ...item, status: nextStatus } : item
    )));

    try {
      const updatedTask = await updateTaskStatus(taskId, nextStatus);

      if (!updatedTask) {
        setTasks(previousTasks);
        setBoardError("Не удалось обновить статус задачи.");
        return;
      }

      setTasks((currentTasks) => currentTasks.map((item) => (
        item.id === taskId ? { ...item, ...updatedTask } : item
      )));
    } catch {
      setTasks(previousTasks);
      setBoardError("Не удалось обновить статус задачи.");
    }
  }

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Project Management</p>
          <h1>Kanban board</h1>
        </section>
        <Link className="button-link" to="/tasks/new">Create Task</Link>
      </header>

      <section className="toolbar">
        <label htmlFor="status">Status</label>
        <select id="status" value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All</option>
          {kanbanStatuses.map((item) => (
            <option key={item} value={item}>{statusLabels[item]}</option>
          ))}
        </select>

        <label htmlFor="assignee">Assignee</label>
        <select id="assignee" value={assignee} onChange={(event) => setAssignee(event.target.value)}>
          <option value="all">All</option>
          {assignees.map((item) => (
            <option key={item ?? "unassigned"} value={String(item)}>
              {item ?? "Unassigned"}
            </option>
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
      {!loading && !error && tasks.length > 0 && (
        <section className="kanban-board" aria-label="Project kanban board">
          {kanbanStatuses.map((item) => {
            const columnTasks = filteredTasks.filter((task) => task.status === item);

            return (
              <article
                className="kanban-column"
                key={item}
                onDragOver={handleDragOver}
                onDrop={(event) => handleDrop(event, item)}
              >
                <header className="kanban-column-header">
                  <h2>{statusLabels[item]}</h2>
                  <span>{columnTasks.length}</span>
                </header>

                <section className="kanban-column-body">
                  {columnTasks.length === 0 && (
                    <p className="kanban-empty">No tasks</p>
                  )}
                  {columnTasks.map((task) => (
                    <TaskCard
                      assigneeName={getUserName(users, task.assignee_id)}
                      creatorName={getUserName(users, task.creator_id)}
                      key={task.id}
                      onDragStart={handleDragStart}
                      task={task}
                    />
                  ))}
                </section>
              </article>
            );
          })}
        </section>
      )}

      {boardError && <section className="page-panel state-error">{boardError}</section>}

      {!loading && !error && users.length > 0 && (
        <section className="project-grid">
          <article className="page-panel page-section">
            <header className="section-header">
              <p>People</p>
              <h2>Project members</h2>
            </header>

            <section className="people-grid">
              {users.map((user) => (
                <article className="person-card" key={user.id}>
                  {user.avatar_url ? (
                    <img alt="" src={user.avatar_url} />
                  ) : (
                    <span className="avatar-fallback">{getInitials(user)}</span>
                  )}
                  <div>
                    <h3>{user.username}</h3>
                    <p>{user.role}</p>
                  </div>
                  <dl>
                    <dt>Team</dt>
                    <dd>{getTeamName(user)}</dd>
                    <dt>Status</dt>
                    <dd>{user.status || "Active"}</dd>
                  </dl>
                </article>
              ))}
            </section>
          </article>

          <article className="page-panel page-section">
            <header className="section-header">
              <p>Teams</p>
              <h2>Project teams</h2>
            </header>

            <section className="teams-list">
              {Object.entries(teams).map(([team, members]) => {
                const lead = members.find((member) => /lead|owner|admin/i.test(member.role)) ?? members[0];

                return (
                  <article className="team-row" key={team}>
                    <div>
                      <h3>{team}</h3>
                      <p>{members.map((member) => member.username).join(", ")}</p>
                    </div>
                    <dl>
                      <dt>People</dt>
                      <dd>{members.length}</dd>
                      <dt>Lead</dt>
                      <dd>{lead?.username ?? "Unassigned"}</dd>
                    </dl>
                  </article>
                );
              })}
            </section>
          </article>
        </section>
      )}

      {!loading && !error && users.length === 0 && (
        <section className="page-panel">Участники проекта пока не добавлены.</section>
      )}
    </main>
  );
}
