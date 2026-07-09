import {
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { getCurrentUser, login as loginRequest, type User } from "../api/authApi";
import { AuthContext } from "./authStore";

function getStoredUser() {
  const value = localStorage.getItem("user");

  if (!value) {
    return null;
  }

  try {
    return JSON.parse(value) as User;
  } catch {
    localStorage.removeItem("user");
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => localStorage.getItem("token"));
  const [user, setUser] = useState<User | null>(() => getStoredUser());

  async function login(email: string, password: string) {
    const response = await loginRequest(email, password);

    try {
      localStorage.setItem("token", response.access_token);
      const currentUser = await getCurrentUser();

      localStorage.setItem("user", JSON.stringify(currentUser));
      setToken(response.access_token);
      setUser(currentUser);
    } catch (error) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      setToken(null);
      setUser(null);
      throw error;
    }
  }

  async function logout() {
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
