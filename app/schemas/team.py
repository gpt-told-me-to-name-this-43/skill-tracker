from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.schemas.user import UserSummary


class TeamCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=500)

    @field_validator("name")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Team name must not be empty")
        return value


class TeamUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=100)
    description: str | None = Field(default=None, max_length=500)

    @field_validator("name")
    @classmethod
    def normalize_name(cls, value: str | None) -> str | None:
        if value is None:
            return None
        value = value.strip()
        if not value:
            raise ValueError("Team name must not be empty")
        return value


class TeamMembersSet(BaseModel):
    user_ids: list[int]
    lead_id: int | None = None

    @field_validator("user_ids")
    @classmethod
    def validate_unique_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("Duplicate user ids are not allowed")
        return value


class TeamRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    description: str | None
    member_count: int
    lead: UserSummary | None
    members: list[UserSummary]
    created_at: datetime
    updated_at: datetime
