from datetime import datetime

from pydantic import BaseModel, Field, field_validator

from app.schemas.experience import TaskSkillRead
from app.schemas.label import LabelRead


class TaskAnalyzeRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    description: str | None = None

    @field_validator("title")
    @classmethod
    def normalize_title(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Title must not be empty")
        return value


class TaskFieldSuggestion(BaseModel):
    difficulty: int = Field(..., ge=1, le=5)
    deadline: datetime | None
    labels: list[LabelRead]
    skills: list[TaskSkillRead]
