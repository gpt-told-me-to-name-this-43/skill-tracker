import { NavLink } from "react-router-dom";

export default function Navbar() {
  return (
    <nav className="navbar">
      <NavLink className="navbar-logo" to="/tasks">Skill Tracker</NavLink>
      <NavLink className="navbar-link" to="/tasks">Tasks</NavLink>
      <NavLink className="navbar-link" to="/tasks/new">Create Task</NavLink>
      <NavLink className="navbar-link" to="/profile">Profile</NavLink>
    </nav>
  );
}
