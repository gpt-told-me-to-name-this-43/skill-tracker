from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.user import UserSummary


def _normalize_name(value: str) -> str:
    value = value.strip()
    if not value:
        raise ValueError("Name must not be empty")
    return value


def _normalize_optional_text(value: str | None) -> str | None:
    if value is None:
        return None

    value = value.strip()
    return value or None


class TeamBrief(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class TeamCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100)
    description: str | None = None

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str) -> str:
        return _normalize_name(value)

    @field_validator("description")
    @classmethod
    def normalize_description(cls, value: str | None) -> str | None:
        return _normalize_optional_text(value)


class TeamUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=100)
    description: str | None = None

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return _normalize_name(value)

    @field_validator("description")
    @classmethod
    def normalize_description(cls, value: str | None) -> str | None:
        return _normalize_optional_text(value)


class TeamMembersSet(BaseModel):
    user_ids: list[int]
    lead_id: int | None = None

    @model_validator(mode="after")
    def validate_unique_user_ids(self) -> "TeamMembersSet":
        if len(self.user_ids) != len(set(self.user_ids)):
            raise ValueError("Duplicate user ids are not allowed")
        return self


class TeamRead(TeamBrief):
    description: str | None
    member_count: int
    lead: UserSummary | None
    members: list[UserSummary]
    created_at: datetime
    updated_at: datetime
