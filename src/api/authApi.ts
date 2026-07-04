export type User = {
  id: number;
  name: string;
  email: string;
};

export type LoginResponse = {
  token: string;
  user: User;
};

const mockUser = {
  id: 1,
  name: "John Smith",
  email: "john@example.com",
};

export function login(email: string, password: string): Promise<LoginResponse> {
  if (!email || !password) {
    return Promise.reject(new Error("Email and password are required"));
  }

  return Promise.resolve({
    token: "mock-auth-token",
    user: {
      ...mockUser,
      email,
    },
  });
}

export function logout(): Promise<void> {
  return Promise.resolve();
}

export function getCurrentUser(): Promise<User> {
  return Promise.resolve(mockUser);
}
