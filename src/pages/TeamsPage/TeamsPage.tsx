import { useCallback, useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  createTeam,
  getTeams,
  getUsers,
  setTeamMembers,
  updateTeam,
} from "../../api/usersApi";
import { useAuth } from "../../context/useAuth";
import type { Person, Team } from "../../types/task";

export default function TeamsPage() {
  const { user } = useAuth();
  const [teams, setTeams] = useState<Team[]>([]);
  const [people, setPeople] = useState<Person[]>([]);
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [memberIds, setMemberIds] = useState<number[]>([]);
  const [leadId, setLeadId] = useState("");
  const [confirmMove, setConfirmMove] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const selectedTeam = useMemo(
    () => teams.find((team) => team.id === selectedTeamId) ?? null,
    [teams, selectedTeamId],
  );
  const canManageProject = user?.role === "admin";

  const movedPeople = useMemo(
    () => people.filter((person) => (
      memberIds.includes(person.id)
      && person.team
      && person.team.id !== selectedTeamId
    )),
    [memberIds, people, selectedTeamId],
  );

  const selectTeam = useCallback((team: Team | null) => {
    setSelectedTeamId(team?.id ?? null);
    setName(team?.name ?? "");
    setDescription(team?.description ?? "");
    setMemberIds(team?.members.map((member) => member.id) ?? []);
    setLeadId(team?.lead?.id ? String(team.lead.id) : "");
    setConfirmMove(false);
    setDrawerOpen(Boolean(team));
  }, []);

  const loadTeams = useCallback(async () => {
    setError("");
    try {
      const [teamsData, peopleData] = await Promise.all([getTeams(), getUsers()]);
      setTeams(teamsData);
      setPeople(peopleData);
      const nextTeam = teamsData.find((team) => team.id === selectedTeamId) ?? teamsData[0] ?? null;
      selectTeam(nextTeam);
      setDrawerOpen(false);
    } catch {
      setError("Не удалось загрузить команды.");
    } finally {
      setLoading(false);
    }
  }, [selectTeam, selectedTeamId]);

  useEffect(() => {
    loadTeams();
  }, [loadTeams]);

  function handleToggleMember(userId: number, checked: boolean) {
    setMemberIds((current) => (
      checked ? [...current, userId] : current.filter((id) => id !== userId)
    ));
    if (!checked && leadId === String(userId)) {
      setLeadId("");
    }
    setConfirmMove(false);
  }

  async function handleCreateTeam(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManageProject) {
      setError("Только project admin может менять команды.");
      return;
    }

    if (!name.trim()) {
      return;
    }

    if (movedPeople.length > 0 && !confirmMove) {
      setConfirmMove(false);
      return;
    }

    setSaving(true);
    setError("");
    try {
      const team = selectedTeam
        ? await updateTeam(selectedTeam.id, { name, description: description || null })
        : await createTeam({ name, description: description || null });

      const updatedTeam = await setTeamMembers(team.id, {
        user_ids: memberIds,
        lead_id: leadId ? Number(leadId) : null,
      });

      setTeams((current) => {
        const exists = current.some((item) => item.id === updatedTeam.id);
        return exists
          ? current.map((item) => (item.id === updatedTeam.id ? updatedTeam : item))
          : [...current, updatedTeam];
      });
      await loadTeams();
      selectTeam(updatedTeam);
      setDrawerOpen(false);
    } catch {
      setError("Не удалось сохранить команду.");
    } finally {
      setSaving(false);
    }
  }

  function handleNewTeam() {
    selectTeam(null);
    setName("");
    setDescription("");
    setMemberIds([]);
    setLeadId("");
    setDrawerOpen(true);
  }

  return (
    <main className="page-shell">
      <header className="page-header page-header-row">
        <section>
          <p>Project</p>
          <h1>Teams</h1>
        </section>
        <button className="button-secondary" disabled={!canManageProject} onClick={handleNewTeam} type="button">New team</button>
      </header>

      <section className="page-panel project-note">
        <strong>Project teams</strong>
        <p>
          Команды собираются из участников текущего проекта. Один человек может быть только в одной команде проекта.
        </p>
      </section>

      {loading && <section className="page-panel">Загрузка команд...</section>}
      {error && <section className="page-panel state-error">{error}</section>}
      {!loading && !error && (
        <section className="teams-page-layout">
          <section className="page-panel teams-list">
            {teams.length === 0 && <p>Команды пока не созданы.</p>}
            {teams.map((team) => (
              <button
                className={`team-card ${team.id === selectedTeamId ? "is-selected" : ""}`}
                key={team.id}
                onClick={() => selectTeam(team)}
                type="button"
              >
                <span>
                  <strong>{team.name}</strong>
                  <small>{team.member_count} people</small>
                </span>
                <em>{team.lead?.username ?? "No lead"}</em>
              </button>
            ))}
          </section>

          {drawerOpen && (
            <section className="drawer-backdrop" aria-label="Team editor">
              <form className="side-drawer team-editor" onSubmit={handleCreateTeam}>
                <header className="drawer-header">
                  <section>
                    <p>{selectedTeam ? "Edit team" : "Create team"}</p>
                    <h2>{selectedTeam?.name ?? "New team"}</h2>
                  </section>
                  <button className="button-secondary icon-button" onClick={() => setDrawerOpen(false)} type="button">X</button>
                </header>

                <label htmlFor="team-name">Name</label>
                <input id="team-name" onChange={(event) => setName(event.target.value)} required value={name} />

                <label htmlFor="team-description">Description</label>
                <textarea id="team-description" onChange={(event) => setDescription(event.target.value)} value={description} />

                <label htmlFor="team-lead">Lead</label>
                <select id="team-lead" onChange={(event) => setLeadId(event.target.value)} value={leadId}>
                  <option value="">No lead</option>
                  {people.filter((person) => memberIds.includes(person.id)).map((person) => (
                    <option key={person.id} value={person.id}>{person.username}</option>
                  ))}
                </select>

                <section className="people-picker">
                  <h3>Members</h3>
                  {people.map((person) => (
                    <label key={person.id}>
                      <input
                        checked={memberIds.includes(person.id)}
                        disabled={!canManageProject}
                        onChange={(event) => handleToggleMember(person.id, event.target.checked)}
                        type="checkbox"
                      />
                      <span>
                        <strong>{person.username}</strong>
                        <small>{person.team?.name ?? "No team"}</small>
                      </span>
                    </label>
                  ))}
                </section>

                {movedPeople.length > 0 && (
                  <section className="move-warning">
                    <strong>Users will be moved from another team</strong>
                    <p>{movedPeople.map((person) => `${person.username} (${person.team?.name})`).join(", ")}</p>
                    <label>
                      <input checked={confirmMove} disabled={!canManageProject} onChange={(event) => setConfirmMove(event.target.checked)} type="checkbox" />
                      <span>Confirm team move</span>
                    </label>
                  </section>
                )}

                {!canManageProject && (
                  <section className="permission-note">
                    Только project admin может менять команды проекта.
                  </section>
                )}

                <button disabled={saving || !canManageProject || (movedPeople.length > 0 && !confirmMove)} type="submit">
                  {saving ? "Saving..." : "Save team"}
                </button>
              </form>
            </section>
          )}
        </section>
      )}
    </main>
  );
}
