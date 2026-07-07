from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import TaskStatus


class TaskCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    description: str | None = None
    difficulty: int = Field(3, ge=1, le=5)
    deadline: datetime | None = None
    assignee_id: int | None = None


class TaskUpdate(BaseModel):
    title: str | None = Field(None, min_length=1, max_length=255)
    description: str | None = None
    difficulty: int | None = Field(None, ge=1, le=5)
    deadline: datetime | None = None


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
