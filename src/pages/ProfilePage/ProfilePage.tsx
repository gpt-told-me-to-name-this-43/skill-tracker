import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentUser, type User } from "../../api/authApi";
import { getUserProgress, getUserSkills, type ProfileProgress, type UserSkill } from "../../api/profileApi";
import ProgressBar from "../../components/ProgressBar/ProgressBar";
import { useAuth } from "../../context/useAuth";

export default function ProfilePage() {
  const { logout, user: storedUser } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<User | null>(storedUser);
  const [skills, setSkills] = useState<UserSkill[]>([]);
  const [progress, setProgress] = useState<ProfileProgress | null>(null);
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
        const [skillsData, progressData] = await Promise.all([
          getUserSkills(currentUser.id),
          getUserProgress(currentUser.id),
        ]);

        setProfile(currentUser);
        setSkills(skillsData);
        setProgress(progressData);
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

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Profile</p>
          <h1>{profile.username}</h1>
        </section>
        <button className="button-secondary" onClick={handleLogout} type="button">
          Выйти
        </button>
      </header>

      <section className="page-panel">
        <dl className="profile-info">
          <dt>Email</dt>
          <dd>{profile.email}</dd>
          <dt>Role</dt>
          <dd>{profile.role}</dd>
          <dt>Total XP</dt>
          <dd>{progress.total_experience}</dd>
          <dt>Average Level</dt>
          <dd>{progress.average_level}</dd>
        </dl>

        <h2>Skills</h2>
        {skills.length === 0 && <p>Навыки пока не добавлены.</p>}
        {skills.map((item) => (
          <article className="skill-row" key={item.skill.id}>
            <span>{item.skill.name}</span>
            <ProgressBar max={100} value={item.progress_to_next_level} />
            <strong>Lvl {item.level}</strong>
            <small>{item.experience} XP</small>
          </article>
        ))}
      </section>
    </main>
  );
}
