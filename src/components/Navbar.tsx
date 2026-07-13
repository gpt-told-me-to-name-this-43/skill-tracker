import { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import {
  GITHUB_SYNC_COMPLETED_EVENT,
  syncGithubIssues,
} from "../api/integrationsApi";
import { useAuth } from "../context/useAuth";

const projectLinks = [
  { to: "/tasks", label: "Board", shortLabel: "B" },
  { to: "/tasks/new", label: "Create Task", shortLabel: "+" },
  { to: "/people", label: "People", shortLabel: "P" },
  { to: "/teams", label: "Teams", shortLabel: "T" },
];

type NavbarProps = {
  collapsed: boolean;
  onToggleCollapsed: () => void;
};

export default function Navbar({ collapsed, onToggleCollapsed }: NavbarProps) {
  const { user } = useAuth();
  const canManageProject = user?.role === "admin";
  const [syncing, setSyncing] = useState(false);
  const [syncMessage, setSyncMessage] = useState("");
  const [syncError, setSyncError] = useState("");

  useEffect(() => {
    if (!syncMessage) {
      return;
    }

    const timer = window.setTimeout(() => setSyncMessage(""), 6000);
    return () => window.clearTimeout(timer);
  }, [syncMessage]);

  async function handleSyncGithub() {
    setSyncing(true);
    setSyncMessage("");
    setSyncError("");
    try {
      const result = await syncGithubIssues();
      setSyncMessage(
        `Imported ${result.created} tasks, ${result.users_created} new profiles`,
      );
      window.dispatchEvent(new Event(GITHUB_SYNC_COMPLETED_EVENT));
    } catch {
      setSyncError("Could not sync GitHub issues.");
    } finally {
      setSyncing(false);
    }
  }

  return (
    <aside className="navbar">
      <section className="sidebar-top">
        <NavLink className="navbar-logo" to="/tasks" title="Skill Tracker">
          <span>ST</span>
          <strong>Skill Tracker</strong>
        </NavLink>

        <button
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          className="sidebar-toggle"
          onClick={onToggleCollapsed}
          type="button"
        >
          {collapsed ? ">" : "<"}
        </button>
      </section>

      <section className="sidebar-project">
        <p>Project</p>
        <h2>Product Workspace</h2>
        <small>{canManageProject ? "Admin access" : "Viewer access"}</small>
      </section>

      <nav className="sidebar-nav" aria-label="Project navigation">
        {projectLinks.map((link) => (
          <NavLink className="navbar-link" key={link.to} title={link.label} to={link.to}>
            <span className="nav-short">{link.shortLabel}</span>
            <span className="nav-label">{link.label}</span>
          </NavLink>
        ))}

        <button
          className="navbar-link sync-github"
          disabled={syncing}
          onClick={handleSyncGithub}
          title="Sync GitHub issues"
          type="button"
        >
          <span className="nav-short">GH</span>
          <span className="nav-label">{syncing ? "Syncing..." : "Sync GitHub issues"}</span>
        </button>
        {syncMessage && <small className="nav-label sync-status">{syncMessage}</small>}
        {syncError && <small className="nav-label sync-status state-error">{syncError}</small>}
      </nav>

      <NavLink className="navbar-link profile-link" title="Profile" to="/profile">
        <span className="nav-short">U</span>
        <span className="nav-label">Profile</span>
      </NavLink>
    </aside>
  );
}
