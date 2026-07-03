const user = {
  name: "John Smith",
  email: "john@example.com",
  totalXp: 1240,
  skills: [
    { name: "Python", progress: 70 },
    { name: "React", progress: 45 },
    { name: "SQL", progress: 60 },
  ],
};

const averageLevel = Math.round(
  user.skills.reduce((sum, skill) => sum + skill.progress, 0) / user.skills.length,
);

export default function ProfilePage() {
  return (
    <main className="page-shell">
      <header className="page-header">
        <p>Profile</p>
        <h1>{user.name}</h1>
      </header>

      <section className="page-panel">
        <dl className="profile-info">
          <dt>Email</dt>
          <dd>{user.email}</dd>
          <dt>Total XP</dt>
          <dd>{user.totalXp}</dd>
          <dt>Average Level</dt>
          <dd>{averageLevel}%</dd>
        </dl>

        <h2>Skills</h2>
        {user.skills.map((skill) => (
          <article className="skill-row" key={skill.name}>
            <span>{skill.name}</span>
            <progress value={skill.progress} max="100" />
            <strong>{skill.progress}%</strong>
          </article>
        ))}
      </section>
    </main>
  );
}
