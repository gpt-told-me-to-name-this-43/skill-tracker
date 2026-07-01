from datetime import datetime

from sqlalchemy import ForeignKey, SmallInteger, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IntPKMixin, TimestampMixin
from app.models.enums import TaskStatus


class Task(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "tasks"

    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[TaskStatus] = mapped_column(default=TaskStatus.todo)
    difficulty: Mapped[int] = mapped_column(SmallInteger, default=3)  # 1..5, см. enums.Difficulty
    deadline: Mapped[datetime | None] = mapped_column(nullable=True)

    creator_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    assignee_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)


class TaskSkill(Base, IntPKMixin):
    __tablename__ = "task_skills"

    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[int] = mapped_column(ForeignKey("skills.id", ondelete="CASCADE"), index=True)
    exp_reward: Mapped[int] = mapped_column(default=0)

    __table_args__ = (UniqueConstraint("task_id", "skill_id", name="uq_task_skill"),)
