from __future__ import annotations

from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    SmallInteger,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, IntPKMixin, TimestampMixin
from app.models.enums import TaskStatus
from app.models.skill import Skill


class Task(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "tasks"

    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[TaskStatus] = mapped_column(default=TaskStatus.todo, index=True)
    difficulty: Mapped[int] = mapped_column(
        SmallInteger,
        default=3,
        index=True,
    )  # 1..5, см. enums.Difficulty
    deadline: Mapped[datetime | None] = mapped_column(nullable=True)

    creator_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    assignee_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id"),
        nullable=True,
        index=True,
    )
    approved_by_id: Mapped[int | None] = mapped_column(
        ForeignKey("users.id"),
        nullable=True,
        index=True,
    )
    approved_at: Mapped[datetime | None] = mapped_column(nullable=True)


class TaskSkill(Base, IntPKMixin):
    __tablename__ = "task_skills"

    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[int] = mapped_column(ForeignKey("skills.id", ondelete="CASCADE"), index=True)
    exp_reward: Mapped[int] = mapped_column()
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    skill: Mapped[Skill] = relationship()

    __table_args__ = (
        UniqueConstraint("task_id", "skill_id", name="uq_task_skill"),
        CheckConstraint("exp_reward > 0", name="ck_task_skills_exp_reward_positive"),
    )
