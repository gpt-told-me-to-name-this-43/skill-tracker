from sqlalchemy import CheckConstraint, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, IntPKMixin, TimestampMixin
from app.models.skill import Skill


class User(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "users"

    username: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    hashed_password: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(20), default="user", server_default="user")
    avatar_url: Mapped[str | None] = mapped_column(String(500), nullable=True)
    position: Mapped[str | None] = mapped_column(String(120), nullable=True)
    member_status: Mapped[str] = mapped_column(String(20), default="active", server_default="active")

    skills: Mapped[list["UserSkill"]] = relationship(back_populates="user")
    team_membership: Mapped["TeamMember | None"] = relationship(
        back_populates="user",
        uselist=False,
    )

    __table_args__ = (
        CheckConstraint(
            "member_status IN ('active', 'away', 'inactive')",
            name="ck_users_member_status",
        ),
    )


class UserSkill(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "user_skills"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[int] = mapped_column(ForeignKey("skills.id", ondelete="CASCADE"), index=True)
    experience: Mapped[int] = mapped_column(default=0)

    user: Mapped["User"] = relationship(back_populates="skills")
    skill: Mapped["Skill"] = relationship()

    __table_args__ = (UniqueConstraint("user_id", "skill_id", name="uq_user_skill"),)


class Team(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "teams"

    name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(String(500), nullable=True)
    lead_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)

    lead: Mapped["User | None"] = relationship(foreign_keys=[lead_id])
    members: Mapped[list["TeamMember"]] = relationship(
        back_populates="team",
        cascade="all, delete-orphan",
    )


class TeamMember(Base, IntPKMixin):
    __tablename__ = "team_members"

    team_id: Mapped[int] = mapped_column(ForeignKey("teams.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)

    team: Mapped["Team"] = relationship(back_populates="members")
    user: Mapped["User"] = relationship(back_populates="team_membership")

    __table_args__ = (
        UniqueConstraint("team_id", "user_id", name="uq_team_member_pair"),
        UniqueConstraint("user_id", name="uq_team_member_user"),
    )
