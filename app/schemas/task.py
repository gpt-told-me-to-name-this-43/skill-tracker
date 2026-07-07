from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.enums import TaskStatus


def _normalize_title(value: str | None) -> str | None:
    if value is None:
        return None

    value = value.strip()
    if not value:
        raise ValueError("Title must not be empty")
    return value


class TaskCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    description: str | None = None
    difficulty: int = Field(3, ge=1, le=5)
    deadline: datetime | None = None
    assignee_id: int | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str) -> str:
        title = _normalize_title(value)
        if title is None:
            raise ValueError("Title must not be empty")
        return title


class TaskUpdate(BaseModel):
    title: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = None
    difficulty: int | None = Field(None, ge=1, le=5)
    deadline: datetime | None = None

    @field_validator("title")
    @classmethod
    def validate_title(cls, value: str | None) -> str | None:
        return _normalize_title(value)


class TaskStatusUpdate(BaseModel):
    status: TaskStatus


class TaskAssign(BaseModel):
    assignee_id: int | None = None


class TaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str | None
    status: TaskStatus
    difficulty: int
    deadline: datetime | None
    creator_id: int
    assignee_id: int | None
    created_at: datetime
    updated_at: datetime
