export type Profile = {
  name: string;
  email: string;
  totalXp: number;
};

export type Skill = {
  id: number;
  name: string;
  level: number;
  experience: number;
};

export type ProfileProgress = {
  averageLevel: number;
};

const mockProfile: Profile = {
  name: "John Smith",
  email: "john@example.com",
  totalXp: 1240,
};

const mockSkills: Skill[] = [
  {
    id: 1,
    name: "Python",
    level: 7,
    experience: 700,
  },
  {
    id: 2,
    name: "React",
    level: 4,
    experience: 450,
  },
  {
    id: 3,
    name: "SQL",
    level: 6,
    experience: 620,
  },
];

const mockProgress: ProfileProgress = {
  averageLevel: Math.round(
    mockSkills.reduce((sum, skill) => sum + skill.level, 0) / mockSkills.length,
  ),
};

export function getProfile(): Promise<Profile> {
  return Promise.resolve(mockProfile);
}

export function getSkills(): Promise<Skill[]> {
  return Promise.resolve(mockSkills);
}

export function getProgress(): Promise<ProfileProgress> {
  return Promise.resolve(mockProgress);
}
