import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { getUserSkills, type UserSkill } from "../../api/profileApi";
import { getTeams, getUsers, updateWorkspaceProfile } from "../../api/usersApi";
import { useAuth } from "../../context/useAuth";
import type { MemberStatus, Person, Team } from "../../types/task";

const memberStatuses: { value: MemberStatus | "all"; label: string }[] = [
  { value: "all", label: "All" },
  { value: "active", label: "Active" },
  { value: "away", label: "Away" },
  { value: "inactive", label: "Inactive" },
];

function initials(name: string) {
  return name
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("") || "U";
}

export default function PeoplePage() {
  const { user } = useAuth();
  const [people, setPeople] = useState<Person[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [teamFilter, setTeamFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState<MemberStatus | "all">("all");
  const [avatarUrl, setAvatarUrl] = useState("");
  const [position, setPosition] = useState("");
  const [memberStatus, setMemberStatus] = useState<MemberStatus>("active");
  const [selectedSkills, setSelectedSkills] = useState<UserSkill[]>([]);
  const [skillsLoading, setSkillsLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const selectedPerson = useMemo(
    () => people.find((person) => person.id === selectedUserId) ?? null,
    [people, selectedUserId],
  );
  const canManageProject = user?.role === "admin";

  useEffect(() => {
    async function loadPeople() {
      setError("");
      try {
        const [peopleData, teamsData] = await Promise.all([
          getUsers({
            team_id: teamFilter === "all" ? undefined : Number(teamFilter),
            member_status: statusFilter,
          }),
          getTeams(),
        ]);

        setPeople(peopleData);
        setTeams(teamsData);
        setSelectedUserId((currentId) => {
          if (peopleData.some((person) => person.id === currentId)) {
            return currentId;
          }

          const nextSelected = peopleData[0] ?? null;
          setAvatarUrl(nextSelected?.avatar_url ?? "");
          setPosition(nextSelected?.position ?? "");
          setMemberStatus(nextSelected?.member_status ?? "active");
          setDrawerOpen(false);
          return nextSelected?.id ?? null;
        });
      } catch {
        setError("Could not load project members.");
      } finally {
        setLoading(false);
      }
    }

    loadPeople();
  }, [statusFilter, teamFilter]);

  function handleSelect(person: Person) {
    setSelectedUserId(person.id);
    setAvatarUrl(person.avatar_url ?? "");
    setPosition(person.position ?? "");
    setMemberStatus(person.member_status);
    setDrawerOpen(true);
  }

  useEffect(() => {
    async function loadSelectedSkills() {
      if (!drawerOpen || !selectedUserId) {
        setSelectedSkills([]);
        return;
      }

      setSkillsLoading(true);
      try {
        setSelectedSkills(await getUserSkills(selectedUserId));
      } catch {
        setSelectedSkills([]);
      } finally {
        setSkillsLoading(false);
      }
    }

    loadSelectedSkills();
  }, [drawerOpen, selectedUserId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPerson) {
      return;
    }

    setSaving(true);
    setError("");
    try {
      const updatedPerson = await updateWorkspaceProfile(selectedPerson.id, {
        avatar_url: avatarUrl.trim() || null,
        position: position.trim() || null,
        member_status: memberStatus,
      });
      setPeople((current) => current.map((person) => (
        person.id === updatedPerson.id ? updatedPerson : person
      )));
      setDrawerOpen(false);
    } catch {
      setError("Could not save the member.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="page-shell">
      <header className="page-header">
        <p>Project</p>
        <h1>People</h1>
      </header>

      <section className="page-panel project-note">
        <strong>Project members</strong>
        <p>
          These people are members of the current project. Only a project admin can edit profiles and move members between teams.
        </p>
      </section>

      <section className="toolbar">
        <label htmlFor="team-filter">Team</label>
        <select id="team-filter" value={teamFilter} onChange={(event) => setTeamFilter(event.target.value)}>
          <option value="all">All</option>
          {teams.map((team) => (
            <option key={team.id} value={team.id}>{team.name}</option>
          ))}
        </select>

        <label htmlFor="status-filter">Status</label>
        <select id="status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as MemberStatus | "all")}>
          {memberStatuses.map((status) => (
            <option key={status.value} value={status.value}>{status.label}</option>
          ))}
        </select>
      </section>

      {loading && <section className="page-panel">Loading members...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && people.length === 0 && (
        <section className="page-panel">No members found.</section>
      )}
      {!loading && !error && people.length > 0 && (
        <section className="people-page-layout">
          <section className="page-panel people-list">
            {people.map((person) => (
              <button
                className={`person-card ${selectedUserId === person.id ? "is-selected" : ""}`}
                key={person.id}
                onClick={() => handleSelect(person)}
                type="button"
              >
                {person.avatar_url ? (
                  <img alt="" src={person.avatar_url} />
                ) : (
                  <span className="avatar-fallback">{initials(person.username)}</span>
                )}
                <span>
                  <strong>{person.username}</strong>
                  <small>{person.position ?? person.role}</small>
                </span>
                <em>{person.member_status}</em>
              </button>
            ))}
          </section>

          {drawerOpen && (
            <section className="drawer-backdrop" aria-label="Member editor">
              <form className="side-drawer member-editor" onSubmit={handleSubmit}>
                <header className="drawer-header">
                  <section>
                    <p>Member</p>
                    <h2>{selectedPerson?.username ?? "Select member"}</h2>
                  </section>
                <button className="button-secondary icon-button" onClick={() => setDrawerOpen(false)} type="button">X</button>
                </header>

                <label htmlFor="avatar-url">Avatar URL</label>
                <input id="avatar-url" onChange={(event) => setAvatarUrl(event.target.value)} value={avatarUrl} />

                <label htmlFor="position">Position</label>
                <input id="position" onChange={(event) => setPosition(event.target.value)} placeholder="Frontend Engineer" value={position} />

                <label htmlFor="member-status">Status</label>
                <select id="member-status" onChange={(event) => setMemberStatus(event.target.value as MemberStatus)} value={memberStatus}>
                  {memberStatuses.filter((status) => status.value !== "all").map((status) => (
                    <option key={status.value} value={status.value}>{status.label}</option>
                  ))}
                </select>

                <section className="detail-grid compact">
                  <article>
                    <span>Role</span>
                    <strong>{selectedPerson?.role ?? "-"}</strong>
                  </article>
                  <article>
                    <span>Team</span>
                    <strong>{selectedPerson?.team?.name ?? "No team"}</strong>
                  </article>
                </section>

                <section className="member-competencies">
                  <header className="section-header">
                    <p>Competencies</p>
                    <h2>Member skills</h2>
                  </header>
                  {skillsLoading && <p>Loading skills...</p>}
                  {!skillsLoading && selectedSkills.length === 0 && <p>No competencies yet</p>}
                  {!skillsLoading && selectedSkills.map((item) => (
                    <article className="skill-row" key={item.skill.id}>
                      <span>{item.skill.name}</span>
                      <strong>Lvl {item.level}</strong>
                      <small>{item.experience} XP</small>
                    </article>
                  ))}
                </section>

            {!canManageProject && (
              <section className="permission-note">
                Only a project admin can edit project members.
              </section>
            )}

            <button disabled={!selectedPerson || saving || !canManageProject} type="submit">
              {saving ? "Saving..." : "Save member"}
            </button>
              </form>
            </section>
          )}
        </section>
      )}
    </main>
  );
}
