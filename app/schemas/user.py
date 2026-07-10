from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, TypeAdapter, field_validator

MemberStatus = Literal["active", "away", "inactive"]


class UserSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    avatar_url: str | None
    position: str | None
    member_status: MemberStatus


class TeamSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class PersonRead(UserSummary):
    role: str
    team: TeamSummary | None = None


class WorkspaceProfileUpdate(BaseModel):
    avatar_url: str | None = None
    position: str | None = Field(default=None, max_length=120)
    member_status: MemberStatus | None = None

    @field_validator("avatar_url")
    @classmethod
    def validate_avatar_url(cls, value: str | None) -> str | None:
        if value is None or value == "":
            return None
        TypeAdapter(HttpUrl).validate_python(value)
        return value


class UserReadWithEmail(UserSummary):
    email: str
    role: str
    created_at: datetime
