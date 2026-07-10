import { Outlet } from "react-router-dom";
import Navbar from "./Navbar";

export default function Layout() {
  return (
    <section className="app-layout">
      <Navbar />
      <section className="app-content">
        <Outlet />
      </section>
    </section>
  );
}
