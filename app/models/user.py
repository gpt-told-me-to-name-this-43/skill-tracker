from sqlalchemy import ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base, IntPKMixin, TimestampMixin


class User(Base, IntPKMixin, TimestampMixin):
    __tablename__ = "users"

    username: Mapped[str] = mapped_column(String(50), unique=True, index=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    hashed_password: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(20), default="member")

    skills: Mapped[list["UserSkill"]] = relationship(back_populates="user")


class UserSkill(Base, IntPKMixin):
    __tablename__ = "user_skills"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[int] = mapped_column(ForeignKey("skills.id", ondelete="CASCADE"), index=True)
    experience: Mapped[int] = mapped_column(default=0)
    level: Mapped[int] = mapped_column(default=1)

    user: Mapped["User"] = relationship(back_populates="skills")

    __table_args__ = (UniqueConstraint("user_id", "skill_id", name="uq_user_skill"),)
