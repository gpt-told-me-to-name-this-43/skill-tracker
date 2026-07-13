from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, TypeAdapter, field_validator

from app.models.enums import TaskStatus
from app.schemas.label import LabelRead
from app.schemas.user import UserSummary


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


class RelatedTaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    status: TaskStatus


class TaskAttachmentCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=200)
    url: str

    @field_validator("name")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Attachment name must not be empty")
        return value

    @field_validator("url")
    @classmethod
    def validate_url(cls, value: str) -> str:
        TypeAdapter(HttpUrl).validate_python(value)
        return value


class TaskAttachmentRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    url: str
    created_by: UserSummary
    created_at: datetime


class TaskRelatedSet(BaseModel):
    task_ids: list[int]

    @field_validator("task_ids")
    @classmethod
    def validate_unique_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("Duplicate task ids are not allowed")
        return value


class TaskListItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    status: TaskStatus
    difficulty: int
    deadline: datetime | None
    creator: UserSummary
    assignee: UserSummary | None
    labels: list[LabelRead]
    attachments_count: int
    related_tasks_count: int
    github_issue_number: int | None = None
    github_url: str | None = None
    created_at: datetime
    updated_at: datetime


class TaskDetail(TaskListItem):
    description: str | None
    attachments: list[TaskAttachmentRead]
    related_tasks: list[RelatedTaskRead]
    creator_id: int
    assignee_id: int | None
    approved_by_id: int | None
    approved_at: datetime | None


LintSeverity = Literal["info", "warning"]


class TaskLintWarningRead(BaseModel):
    code: str
    field: str | None
    severity: LintSeverity
    message: str


class TaskLintReport(BaseModel):
    task_id: int
    warnings: list[TaskLintWarningRead]


TaskRead = TaskDetail
