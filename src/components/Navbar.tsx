import { NavLink } from "react-router-dom";
import { useAuth } from "../context/useAuth";

const projectLinks = [
  { to: "/tasks", label: "Board" },
  { to: "/tasks/new", label: "Create Task" },
  { to: "/people", label: "People" },
  { to: "/teams", label: "Teams" },
];

export default function Navbar() {
  const { user } = useAuth();
  const canManageProject = user?.role === "admin";

  return (
    <aside className="navbar">
      <NavLink className="navbar-logo" to="/tasks">
        <span>ST</span>
        Skill Tracker
      </NavLink>

      <section className="sidebar-project">
        <p>Project</p>
        <h2>Product Workspace</h2>
        <small>{canManageProject ? "Admin access" : "Viewer access"}</small>
      </section>

      <nav className="sidebar-nav" aria-label="Project navigation">
        {projectLinks.map((link) => (
          <NavLink className="navbar-link" key={link.to} to={link.to}>
            {link.label}
          </NavLink>
        ))}
      </nav>

      <section className="sidebar-access">
        <strong>Project access</strong>
        <p>{canManageProject ? "You can manage members and teams." : "Ask an admin to change members or teams."}</p>
      </section>

      <NavLink className="navbar-link profile-link" to="/profile">
        Profile
      </NavLink>
    </aside>
  );
}
