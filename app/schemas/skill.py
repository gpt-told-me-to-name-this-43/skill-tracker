from pydantic import BaseModel, ConfigDict, Field


class SkillBase(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    description: str | None = None


class SkillCreate(SkillBase):
    model_config = ConfigDict(extra="forbid")


class SkillUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=100)
    description: str | None = None


class SkillRead(SkillBase):
    model_config = ConfigDict(from_attributes=True)
    id: int


class UserSkillAssign(BaseModel):
    skill_id: int


class UserSkillRead(BaseModel):
    skill: SkillRead
    experience: int
    level: int
    current_level_xp: int
    next_level_xp: int
    progress_to_next_level: int


class UserProgressRead(BaseModel):
    user_id: int
    total_experience: int
    skills_count: int
    average_level: float
    skills: list[UserSkillRead]
