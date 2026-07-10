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
from app.models.user import User


class Label(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "labels"

    name: Mapped[str] = mapped_column(String(80), nullable=False)
    color: Mapped[str | None] = mapped_column(String(7), nullable=True)


class Task(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "tasks"

    title: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    status: Mapped[TaskStatus] = mapped_column(default=TaskStatus.todo, index=True)
    difficulty: Mapped[int] = mapped_column(
        SmallInteger,
        default=3,
        index=True,
    )
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

    creator: Mapped[User] = relationship(foreign_keys=[creator_id])
    assignee: Mapped[User | None] = relationship(foreign_keys=[assignee_id])
    labels: Mapped[list[TaskLabel]] = relationship(
        back_populates="task",
        cascade="all, delete-orphan",
    )
    attachments: Mapped[list[TaskAttachment]] = relationship(
        back_populates="task",
        cascade="all, delete-orphan",
    )
    left_relations: Mapped[list[TaskRelation]] = relationship(
        foreign_keys="TaskRelation.left_task_id",
        cascade="all, delete-orphan",
    )
    right_relations: Mapped[list[TaskRelation]] = relationship(
        foreign_keys="TaskRelation.right_task_id",
        cascade="all, delete-orphan",
    )


class TaskLabel(Base, IntPKMixin):
    __tablename__ = "task_labels"

    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), index=True)
    label_id: Mapped[int] = mapped_column(ForeignKey("labels.id", ondelete="CASCADE"), index=True)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    task: Mapped[Task] = relationship(back_populates="labels")
    label: Mapped[Label] = relationship()

    __table_args__ = (UniqueConstraint("task_id", "label_id", name="uq_task_label"),)


class TaskAttachment(Base, IntPKMixin):
    __tablename__ = "task_attachments"

    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id", ondelete="CASCADE"), index=True)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    url: Mapped[str] = mapped_column(String(1000), nullable=False)
    created_by_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    task: Mapped[Task] = relationship(back_populates="attachments")
    created_by: Mapped[User] = relationship()


class TaskRelation(Base, IntPKMixin):
    __tablename__ = "task_relations"

    left_task_id: Mapped[int] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"),
        index=True,
    )
    right_task_id: Mapped[int] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"),
        index=True,
    )
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    left_task: Mapped[Task] = relationship(foreign_keys=[left_task_id])
    right_task: Mapped[Task] = relationship(foreign_keys=[right_task_id])

    __table_args__ = (
        UniqueConstraint("left_task_id", "right_task_id", name="uq_task_relation"),
        CheckConstraint("left_task_id < right_task_id", name="ck_task_relation_order"),
    )


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
