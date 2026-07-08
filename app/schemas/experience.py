from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.schemas.skill import SkillRead


class TaskSkillItem(BaseModel):
    skill_id: int
    exp_reward: int = Field(..., gt=0, le=1000)


class TaskSkillsSet(BaseModel):
    skills: list[TaskSkillItem]

    @field_validator("skills")
    @classmethod
    def validate_unique_skill_ids(cls, value: list[TaskSkillItem]) -> list[TaskSkillItem]:
        skill_ids = [item.skill_id for item in value]
        if len(skill_ids) != len(set(skill_ids)):
            raise ValueError("skill_id values must be unique")
        return value


class TaskSkillRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    skill: SkillRead
    exp_reward: int


class ExperienceLogRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    skill_id: int
    task_id: int
    amount: int
    created_at: datetime
