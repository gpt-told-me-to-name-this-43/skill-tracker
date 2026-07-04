import {
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { login as loginRequest, logout as logoutRequest, type User } from "../api/authApi";
import { AuthContext } from "./authStore";

function getStoredUser() {
  const value = localStorage.getItem("user");

  if (!value) {
    return null;
  }

  return JSON.parse(value) as User;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => localStorage.getItem("token"));
  const [user, setUser] = useState<User | null>(() => getStoredUser());

  async function login(email: string, password: string) {
    const response = await loginRequest(email, password);

    localStorage.setItem("token", response.token);
    localStorage.setItem("user", JSON.stringify(response.user));
    setToken(response.token);
    setUser(response.user);
  }

  async function logout() {
    await logoutRequest();

    localStorage.removeItem("token");
    localStorage.removeItem("user");
    setToken(null);
    setUser(null);
  }

  const value = useMemo(
    () => ({
      token,
      user,
      login,
      logout,
      isAuthenticated: Boolean(token),
    }),
    [token, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
