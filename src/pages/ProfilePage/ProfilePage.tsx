import { useEffect, useState } from "react";
import { getProfile, getProgress, getSkills, type Profile, type ProfileProgress, type Skill } from "../../api/profileApi";
import ProgressBar from "../../components/ProgressBar/ProgressBar";

export default function ProfilePage() {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [skills, setSkills] = useState<Skill[]>([]);
  const [progress, setProgress] = useState<ProfileProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadProfile() {
      try {
        const [profileData, skillsData, progressData] = await Promise.all([
          getProfile(),
          getSkills(),
          getProgress(),
        ]);

        setProfile(profileData);
        setSkills(skillsData);
        setProgress(progressData);
      } catch {
        setError("Не удалось загрузить профиль.");
      } finally {
        setLoading(false);
      }
    }

    loadProfile();
  }, []);

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
      <header className="page-header">
        <p>Profile</p>
        <h1>{profile.name}</h1>
      </header>

      <section className="page-panel">
        <dl className="profile-info">
          <dt>Email</dt>
          <dd>{profile.email}</dd>
          <dt>Total XP</dt>
          <dd>{profile.totalXp}</dd>
          <dt>Average Level</dt>
          <dd>{progress.averageLevel}</dd>
        </dl>

        <h2>Skills</h2>
        {skills.length === 0 && <p>Навыки пока не добавлены.</p>}
        {skills.map((skill) => (
          <article className="skill-row" key={skill.id}>
            <span>{skill.name}</span>
            <ProgressBar max={10} value={skill.level} />
            <strong>Lvl {skill.level}</strong>
            <small>{skill.experience} XP</small>
          </article>
        ))}
      </section>
    </main>
  );
}
