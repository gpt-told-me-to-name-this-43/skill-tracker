import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import type { DragEvent, FormEvent } from "react";
import { getTasks, updateTaskStatus } from "../../api/tasksApi";
import { getUsers, type User } from "../../api/usersApi";
import TaskCard from "../../components/TaskCard/TaskCard";
import { statusLabels } from "../../constants/taskStatus";
import type { Task, TaskStatus } from "../../types/task";

const kanbanStatuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];
const memberStatuses = ["Active", "Busy", "Reviewing", "Offline"];
type ProjectView = "board" | "people";

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

function createProjectUser(id: number, name: string, role: string, team: string): User {
  const normalizedName = name.trim() || "New Member";

  return {
    id,
    email: `${normalizedName.toLowerCase().replace(/\s+/g, ".")}@example.com`,
    username: normalizedName,
    role: role.trim() || "Member",
    avatar_url: null,
    team: team.trim() || "Project Team",
    status: "Active",
    created_at: new Date().toISOString(),
  };
}

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [memberName, setMemberName] = useState("");
  const [memberRole, setMemberRole] = useState("");
  const [memberTeam, setMemberTeam] = useState("");
  const [newMemberName, setNewMemberName] = useState("");
  const [newMemberRole, setNewMemberRole] = useState("Member");
  const [newMemberTeam, setNewMemberTeam] = useState("Project Team");
  const [newTeamName, setNewTeamName] = useState("");
  const [newTeamLeadId, setNewTeamLeadId] = useState("");
  const [projectView, setProjectView] = useState<ProjectView>("board");
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
        const firstUser = usersData[0];
        setSelectedUserId(firstUser?.id ?? null);
        setMemberName(firstUser?.username ?? "");
        setMemberRole(firstUser?.role ?? "");
        setMemberTeam(firstUser ? getTeamName(firstUser) : "");
      } catch {
        setError("Не удалось загрузить проект.");
      } finally {
        setLoading(false);
      }
    }

    loadProject();
  }, []);

  const selectedUser = users.find((user) => user.id === selectedUserId) ?? null;
  const assignees = [...new Set(tasks.map((task) => task.assignee_id))]
    .sort((first, second) => Number(first ?? 0) - Number(second ?? 0));
  const difficulties = [...new Set(tasks.map((task) => task.difficulty))];
  const teams = users.reduce<Record<string, User[]>>((acc, user) => {
    const team = getTeamName(user);
    acc[team] = [...(acc[team] ?? []), user];
    return acc;
  }, {});
  const teamNames = Object.keys(teams);

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

  function handleSelectUser(user: User) {
    setSelectedUserId(user.id);
    setMemberName(user.username);
    setMemberRole(user.role);
    setMemberTeam(getTeamName(user));
  }

  function handleMemberSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!selectedUser) {
      return;
    }

    setUsers((currentUsers) => currentUsers.map((user) => (
      user.id === selectedUser.id
        ? {
          ...user,
          username: memberName.trim() || user.username,
          role: memberRole.trim() || user.role,
          team: memberTeam.trim() || getTeamName(user),
        }
        : user
    )));
  }

  function handleMemberStatus(userId: number, nextStatus: string) {
    setUsers((currentUsers) => currentUsers.map((user) => (
      user.id === userId ? { ...user, status: nextStatus } : user
    )));
  }

  function handleAddMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const nextId = Math.max(0, ...users.map((user) => user.id)) + 1;
    const user = createProjectUser(nextId, newMemberName, newMemberRole, newMemberTeam);

    setUsers((currentUsers) => [...currentUsers, user]);
    setSelectedUserId(user.id);
    setMemberName(user.username);
    setMemberRole(user.role);
    setMemberTeam(getTeamName(user));
    setNewMemberName("");
    setNewMemberRole("Member");
    setNewMemberTeam(getTeamName(user));
  }

  function handleCreateTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const teamName = newTeamName.trim();

    if (!teamName) {
      return;
    }

    const leadId = Number(newTeamLeadId);

    setUsers((currentUsers) => {
      if (!leadId) {
        const nextId = Math.max(0, ...currentUsers.map((user) => user.id)) + 1;
        return [...currentUsers, createProjectUser(nextId, `${teamName} Lead`, "Lead", teamName)];
      }

      return currentUsers.map((user) => (
        user.id === leadId ? { ...user, team: teamName, role: user.role || "Lead" } : user
      ));
    });

    setNewTeamName("");
    setNewTeamLeadId("");
  }

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Project Management</p>
          <h1>{projectView === "board" ? "Kanban board" : "People & teams"}</h1>
        </section>
        <Link className="button-link" to="/tasks/new">Create Task</Link>
      </header>

      <nav className="project-tabs" aria-label="Project sections">
        <button
          className={projectView === "board" ? "is-active" : ""}
          onClick={() => setProjectView("board")}
          type="button"
        >
          Board
        </button>
        <button
          className={projectView === "people" ? "is-active" : ""}
          onClick={() => setProjectView("people")}
          type="button"
        >
          People & Teams
        </button>
      </nav>

      {projectView === "board" && (
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
      )}

      {loading && <section className="page-panel">Загрузка задач...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {projectView === "board" && !loading && !error && tasks.length === 0 && (
        <section className="page-panel">Пока нет задач.</section>
      )}
      {projectView === "board" && !loading && !error && tasks.length > 0 && filteredTasks.length === 0 && (
        <section className="page-panel">Задачи не найдены.</section>
      )}
      {projectView === "board" && !loading && !error && tasks.length > 0 && (
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

      {projectView === "board" && boardError && <section className="page-panel state-error">{boardError}</section>}

      {projectView === "people" && !loading && !error && users.length > 0 && (
        <section className="project-collaboration">
          <article className="page-panel page-section">
            <header className="section-header">
              <p>People</p>
              <h2>Project members</h2>
            </header>

            <section className="people-workspace">
              <div className="people-list">
                {users.map((user) => (
                  <button
                    className={`person-card ${selectedUserId === user.id ? "is-selected" : ""}`}
                    key={user.id}
                    onClick={() => handleSelectUser(user)}
                    type="button"
                  >
                    {user.avatar_url ? (
                      <img alt="" src={user.avatar_url} />
                    ) : (
                      <span className="avatar-fallback">{getInitials(user)}</span>
                    )}
                    <span>
                      <strong>{user.username}</strong>
                      <small>{user.role}</small>
                    </span>
                    <em>{user.status || "Active"}</em>
                  </button>
                ))}
              </div>

              <div className="member-forms">
                <form className="member-editor" onSubmit={handleMemberSubmit}>
                  <h3>{selectedUser ? "Edit member" : "Select member"}</h3>

                  <label htmlFor="member-name">Name</label>
                  <input
                    disabled={!selectedUser}
                    id="member-name"
                    onChange={(event) => setMemberName(event.target.value)}
                    value={memberName}
                  />

                  <label htmlFor="member-role">Role</label>
                  <input
                    disabled={!selectedUser}
                    id="member-role"
                    onChange={(event) => setMemberRole(event.target.value)}
                    value={memberRole}
                  />

                  <label htmlFor="member-team">Team</label>
                  <input
                    disabled={!selectedUser}
                    id="member-team"
                    list="team-options"
                    onChange={(event) => setMemberTeam(event.target.value)}
                    value={memberTeam}
                  />
                  <datalist id="team-options">
                    {teamNames.map((team) => (
                      <option key={team} value={team} />
                    ))}
                  </datalist>

                  <button disabled={!selectedUser} type="submit">Save member</button>
                </form>

                <form className="member-editor" onSubmit={handleAddMember}>
                  <h3>Add member</h3>

                  <label htmlFor="new-member-name">Name</label>
                  <input
                    id="new-member-name"
                    onChange={(event) => setNewMemberName(event.target.value)}
                    required
                    value={newMemberName}
                  />

                  <label htmlFor="new-member-role">Role</label>
                  <input
                    id="new-member-role"
                    onChange={(event) => setNewMemberRole(event.target.value)}
                    value={newMemberRole}
                  />

                  <label htmlFor="new-member-team">Team</label>
                  <input
                    id="new-member-team"
                    list="team-options"
                    onChange={(event) => setNewMemberTeam(event.target.value)}
                    value={newMemberTeam}
                  />

                  <button type="submit">Add member</button>
                </form>
              </div>
            </section>
          </article>

          <article className="page-panel page-section">
            <header className="section-header">
              <p>Teams</p>
              <h2>Project teams</h2>
            </header>

            <form className="team-form" onSubmit={handleCreateTeam}>
              <label htmlFor="new-team-name">Team name</label>
              <input
                id="new-team-name"
                onChange={(event) => setNewTeamName(event.target.value)}
                placeholder="Frontend Team"
                value={newTeamName}
              />

              <label htmlFor="new-team-lead">Lead</label>
              <select
                id="new-team-lead"
                onChange={(event) => setNewTeamLeadId(event.target.value)}
                value={newTeamLeadId}
              >
                <option value="">Create new lead</option>
                {users.map((user) => (
                  <option key={user.id} value={user.id}>{user.username}</option>
                ))}
              </select>

              <button type="submit">Create team</button>
            </form>

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
                    <section className="team-members">
                      {members.map((member) => (
                        <label key={member.id}>
                          <span>{member.username}</span>
                          <select
                            onChange={(event) => handleMemberStatus(member.id, event.target.value)}
                            value={member.status || "Active"}
                          >
                            {memberStatuses.map((item) => (
                              <option key={item} value={item}>{item}</option>
                            ))}
                          </select>
                        </label>
                      ))}
                    </section>
                  </article>
                );
              })}
            </section>
          </article>
        </section>
      )}

      {projectView === "people" && !loading && !error && users.length === 0 && (
        <section className="page-panel">Участники проекта пока не добавлены.</section>
      )}
    </main>
  );
}
