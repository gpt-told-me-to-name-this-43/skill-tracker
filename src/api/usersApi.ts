export type User = {
  id: number;
  name: string;
  email: string;
};

const mockUsers: User[] = [
  {
    id: 1,
    name: "John",
    email: "john@example.com",
  },
  {
    id: 2,
    name: "Anna",
    email: "anna@example.com",
  },
  {
    id: 3,
    name: "Kate",
    email: "kate@example.com",
  },
];

export function getUsers(): Promise<User[]> {
  return Promise.resolve(mockUsers);
}
