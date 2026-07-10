import { NavLink } from "react-router-dom";
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
      </nav>

      <section className="sidebar-access">
        <strong>Project access</strong>
        <p>{canManageProject ? "You can manage members and teams." : "Ask an admin to change members or teams."}</p>
      </section>

      <NavLink className="navbar-link profile-link" title="Profile" to="/profile">
        <span className="nav-short">U</span>
        <span className="nav-label">Profile</span>
      </NavLink>
    </aside>
  );
}
