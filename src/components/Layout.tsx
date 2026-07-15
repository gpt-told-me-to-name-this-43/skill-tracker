import { Outlet } from "react-router-dom";
import { useState } from "react";
import Navbar from "./Navbar";

export default function Layout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <section className={`app-layout ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      <Navbar
        collapsed={sidebarCollapsed}
        onToggleCollapsed={() => setSidebarCollapsed((current) => !current)}
      />
      <section className="app-content">
        <Outlet />
      </section>
    </section>
  );
}
