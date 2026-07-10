import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getCurrentUser, type User } from "../../api/authApi";
import { getUserProgress, getUserSkills, type ProfileProgress, type UserSkill } from "../../api/profileApi";
import { getTasks } from "../../api/tasksApi";
import ProgressBar from "../../components/ProgressBar/ProgressBar";
import StatusBadge from "../../components/StatusBadge/StatusBadge";
import { statusLabels } from "../../constants/taskStatus";
import { useAuth } from "../../context/useAuth";
import type { TaskListItem, TaskStatus } from "../../types/task";

type ProfileView = "overview" | "work" | "activity";

const profileViews: { id: ProfileView; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "work", label: "Assigned Work" },
  { id: "activity", label: "Activity" },
];

const workStatuses: TaskStatus[] = ["todo", "in_progress", "review", "done"];

function getInitials(user: User) {
  return user.username
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "U";
}

function getAvailability(user: User) {
  return user.member_status;
}

function getTaskDate(task: TaskListItem) {
  return task.updated_at || task.created_at;
}

export default function ProfilePage() {
  const { logout, user: storedUser } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<User | null>(storedUser);
  const [skills, setSkills] = useState<UserSkill[]>([]);
  const [progress, setProgress] = useState<ProfileProgress | null>(null);
  const [tasks, setTasks] = useState<TaskListItem[]>([]);
  const [profileView, setProfileView] = useState<ProfileView>("overview");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  useEffect(() => {
    async function loadProfile() {
      try {
        const currentUser = storedUser ?? await getCurrentUser();
        const [skillsData, progressData, tasksData] = await Promise.all([
          getUserSkills(currentUser.id),
          getUserProgress(currentUser.id),
          getTasks(),
        ]);

        setProfile(currentUser);
        setSkills(skillsData);
        setProgress(progressData);
        setTasks(tasksData);
      } catch {
        setError("Не удалось загрузить профиль.");
      } finally {
        setLoading(false);
      }
    }

    loadProfile();
  }, [storedUser]);

  if (loading) {
    return (
      <main className="page-shell">
        <section className="page-panel">Загрузка профиля...</section>
      </main>
    );
  }

  if (error || !profile || !progress) {
    return (
      <main className="page-shell">
        <section className="page-panel state-error">{error || "Профиль не найден."}</section>
      </main>
    );
  }

  const assignedTasks = tasks.filter((task) => task.assignee?.id === profile.id);
  const createdTasks = tasks.filter((task) => task.creator.id === profile.id);
  const completedTasks = assignedTasks.filter((task) => task.status === "done");
  const activeTasks = assignedTasks.filter((task) => task.status !== "done");
  const reviewTasks = assignedTasks.filter((task) => task.status === "review");
  const statusCounts = workStatuses.map((status) => ({
    status,
    count: assignedTasks.filter((task) => task.status === status).length,
  }));
  const activityItems = [...assignedTasks, ...createdTasks]
    .filter((task, index, list) => list.findIndex((item) => item.id === task.id) === index)
    .sort((first, second) => getTaskDate(second).localeCompare(getTaskDate(first)))
    .slice(0, 8);

  return (
    <main className="page-shell">
      <section className="profile-hero">
        <div className="profile-avatar">
          {profile.avatar_url ? (
            <img alt="" src={profile.avatar_url} />
          ) : (
            <span>{getInitials(profile)}</span>
          )}
        </div>

        <section className="profile-heading">
          <p>Profile</p>
          <h1>{profile.username}</h1>
          <ul className="profile-tags">
            <li>{profile.role}</li>
            <li>{profile.position ?? "No position"}</li>
            <li>{getAvailability(profile)}</li>
          </ul>
        </section>

        <button className="button-secondary" onClick={handleLogout} type="button">
          Выйти
        </button>
      </section>

      <nav className="project-tabs profile-tabs" aria-label="Profile sections">
        {profileViews.map((view) => (
          <button
            className={profileView === view.id ? "is-active" : ""}
            key={view.id}
            onClick={() => setProfileView(view.id)}
            type="button"
          >
            {view.label}
          </button>
        ))}
      </nav>

      <section className="profile-layout">
        <aside className="profile-sidebar">
          <section className="page-panel profile-card">
            <h2>Details</h2>
            <dl className="profile-info">
              <dt>Email</dt>
              <dd>{profile.email}</dd>
              <dt>Role</dt>
              <dd>{profile.role}</dd>
              <dt>Team</dt>
              <dd>{profile.position ?? "No position"}</dd>
              <dt>Availability</dt>
              <dd>{getAvailability(profile)}</dd>
            </dl>
          </section>

          <section className="page-panel profile-card">
            <h2>Skills</h2>
            {skills.length === 0 && <p>Навыки пока не добавлены.</p>}
            <section className="profile-skills">
              {skills.map((item) => (
                <article className="skill-row" key={item.skill.id}>
                  <span>{item.skill.name}</span>
                  <ProgressBar max={100} value={item.progress_to_next_level} />
                  <strong>Lvl {item.level}</strong>
                  <small>{item.experience} XP</small>
                </article>
              ))}
            </section>
          </section>
        </aside>

        <section className="profile-main">
          {profileView === "overview" && (
            <>
              <section className="profile-metrics">
                <article className="metric-card">
                  <span>{activeTasks.length}</span>
                  <p>Active work</p>
                </article>
                <article className="metric-card">
                  <span>{reviewTasks.length}</span>
                  <p>In review</p>
                </article>
                <article className="metric-card">
                  <span>{completedTasks.length}</span>
                  <p>Completed</p>
                </article>
                <article className="metric-card">
                  <span>{progress.total_experience}</span>
                  <p>Total XP</p>
                </article>
              </section>

              <section className="page-panel profile-card">
                <header className="section-header">
                  <p>Workload</p>
                  <h2>Assigned work by status</h2>
                </header>
                <section className="workload-grid">
                  {statusCounts.map((item) => (
                    <article key={item.status}>
                      <StatusBadge status={item.status} />
                      <strong>{item.count}</strong>
                      <span>{statusLabels[item.status]}</span>
                    </article>
                  ))}
                </section>
              </section>

              <section className="page-panel profile-card">
                <header className="section-header">
                  <p>Contribution</p>
                  <h2>Created by user</h2>
                </header>
                {createdTasks.length === 0 && <p>Пока нет созданных задач.</p>}
                <section className="profile-task-list">
                  {createdTasks.slice(0, 4).map((task) => (
                    <Link className="profile-task-row" key={task.id} to={`/tasks/${task.id}`}>
                      <span>#{task.id}</span>
                      <strong>{task.title}</strong>
                      <StatusBadge status={task.status} />
                    </Link>
                  ))}
                </section>
              </section>
            </>
          )}

          {profileView === "work" && (
            <section className="page-panel profile-card">
              <header className="section-header">
                <p>Assigned Work</p>
                <h2>Tasks assigned to {profile.username}</h2>
              </header>
              {assignedTasks.length === 0 && <p>Назначенных задач пока нет.</p>}
              <section className="profile-work-columns">
                {workStatuses.map((status) => {
                  const statusTasks = assignedTasks.filter((task) => task.status === status);

                  return (
                    <article className="profile-work-column" key={status}>
                      <header>
                        <h3>{statusLabels[status]}</h3>
                        <span>{statusTasks.length}</span>
                      </header>
                      {statusTasks.length === 0 && <p>No tasks</p>}
                      {statusTasks.map((task) => (
                        <Link className="profile-task-row" key={task.id} to={`/tasks/${task.id}`}>
                          <span>#{task.id}</span>
                          <strong>{task.title}</strong>
                          <small>{task.deadline ?? "No deadline"}</small>
                        </Link>
                      ))}
                    </article>
                  );
                })}
              </section>
            </section>
          )}

          {profileView === "activity" && (
            <section className="page-panel profile-card">
              <header className="section-header">
                <p>Activity</p>
                <h2>Recent work activity</h2>
              </header>
              {activityItems.length === 0 && <p>Активности пока нет.</p>}
              <ol className="activity-list">
                {activityItems.map((task) => (
                  <li key={task.id}>
                    <span />
                    <section>
                      <p>
                        {task.creator.id === profile.id ? "Created" : "Worked on"} task
                        {" "}
                        <Link className="page-link" to={`/tasks/${task.id}`}>#{task.id}</Link>
                      </p>
                      <strong>{task.title}</strong>
                      <small>{statusLabels[task.status]} · {getTaskDate(task)}</small>
                    </section>
                  </li>
                ))}
              </ol>
            </section>
          )}
        </section>
      </section>
    </main>
  );
}
