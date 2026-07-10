import { Routes, Route, Navigate } from "react-router-dom";
import LoginPage from "../pages/LoginPage";
import TasksPage from "../pages/TasksPage/TasksPage";
import CreateTaskPage from "../pages/CreateTaskPage/CreateTaskPage";
import TaskDetailsPage from "../pages/TaskDetailsPage/TaskDetailsPage";
import ProfilePage from "../pages/ProfilePage/ProfilePage";
import PeoplePage from "../pages/PeoplePage/PeoplePage";
import TeamsPage from "../pages/TeamsPage/TeamsPage";
import Layout from "../components/Layout";
import PrivateRoute from "./PrivateRoute";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/tasks" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route element={<PrivateRoute><Layout /></PrivateRoute>}>
        <Route path="/tasks" element={<TasksPage />} />
        <Route path="/tasks/new" element={<CreateTaskPage />} />
        <Route path="/tasks/:taskId" element={<TaskDetailsPage />} />
        <Route path="/people" element={<PeoplePage />} />
        <Route path="/teams" element={<TeamsPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
    </Routes>
  );
}
