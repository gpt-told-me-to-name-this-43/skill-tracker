from datetime import datetime

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    HttpUrl,
    TypeAdapter,
    field_validator,
    model_validator,
)

from app.models.enums import MemberStatus


def _normalize_optional_text(value: str | None) -> str | None:
    if value is None:
        return None

    value = value.strip()
    return value or None


class UserSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    avatar_url: str | None
    position: str | None
    member_status: MemberStatus
    github_login: str | None = None
    is_placeholder: bool = False

    @field_validator("is_placeholder", mode="before")
    @classmethod
    def default_placeholder(cls, value: bool | None) -> bool:
        # У не-flushed ORM-инстансов default колонки ещё не применён.
        return bool(value) if value is not None else False


class UserRead(UserSummary):
    email: str
    role: str
    created_at: datetime


class UserTeamBrief(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class UserPublicRead(UserSummary):
    role: str
    team: UserTeamBrief | None = None
    created_at: datetime


class UserWorkspaceProfileUpdate(BaseModel):
    avatar_url: str | None = Field(default=None, max_length=2048)
    position: str | None = Field(default=None, max_length=100)
    member_status: MemberStatus | None = None

    @field_validator("avatar_url", "position")
    @classmethod
    def normalize_text(cls, value: str | None) -> str | None:
        return _normalize_optional_text(value)

    @field_validator("avatar_url", mode="after")
    @classmethod
    def validate_avatar_url(cls, value: str | None) -> str | None:
        if value is None:
            return None
        TypeAdapter(HttpUrl).validate_python(value)
        return value

    @model_validator(mode="after")
    def validate_member_status(self) -> "UserWorkspaceProfileUpdate":
        if "member_status" in self.model_fields_set and self.member_status is None:
            raise ValueError("member_status must not be null")
        return self
