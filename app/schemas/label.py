import re
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

COLOR_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")


class LabelCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=80)
    color: str | None = None

    @field_validator("name")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Label name must not be empty")
        return value

    @field_validator("color")
    @classmethod
    def validate_color(cls, value: str | None) -> str | None:
        if value is None or value == "":
            return None
        if not COLOR_RE.match(value):
            raise ValueError("Color must use #RRGGBB format")
        return value


class LabelRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    color: str | None
    created_at: datetime
    updated_at: datetime


class TaskLabelsSet(BaseModel):
    label_ids: list[int]

    @field_validator("label_ids")
    @classmethod
    def validate_unique_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("Duplicate label ids are not allowed")
        return value
