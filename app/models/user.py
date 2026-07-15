from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, IntPKMixin, TimestampMixin
from app.models.enums import MemberStatus
from app.models.skill import Skill

if TYPE_CHECKING:
    from app.models.team import Team, TeamMember


class User(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "users"

    username: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    hashed_password: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(20), default="user", server_default="user")
    avatar_url: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    position: Mapped[str | None] = mapped_column(String(100), nullable=True)
    member_status: Mapped[str] = mapped_column(
        String(20),
        default=MemberStatus.active.value,
        server_default=MemberStatus.active.value,
        nullable=False,
    )
    github_login: Mapped[str | None] = mapped_column(
        String(100),
        unique=True,
        index=True,
        nullable=True,
    )
    # Профиль-заглушка, созданный импортом из GitHub: логин в приложение невозможен.
    is_placeholder: Mapped[bool] = mapped_column(
        default=False,
        server_default="false",
        nullable=False,
    )

    skills: Mapped[list[UserSkill]] = relationship(back_populates="user")
    team_membership: Mapped[TeamMember | None] = relationship(
        back_populates="user",
        uselist=False,
        cascade="all, delete-orphan",
    )

    @property
    def team(self) -> Team | None:
        if self.team_membership is None:
            return None
        return self.team_membership.team


class UserSkill(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "user_skills"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[int] = mapped_column(ForeignKey("skills.id", ondelete="CASCADE"), index=True)
    experience: Mapped[int] = mapped_column(default=0)

    user: Mapped[User] = relationship(back_populates="skills")
    skill: Mapped[Skill] = relationship()

    __table_args__ = (UniqueConstraint("user_id", "skill_id", name="uq_user_skill"),)
