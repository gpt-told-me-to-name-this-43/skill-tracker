from datetime import datetime

from sqlalchemy import CheckConstraint, ForeignKey, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IntPKMixin


class ExperienceLog(Base, IntPKMixin):
    __tablename__ = "experience_logs"

    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    skill_id: Mapped[int] = mapped_column(ForeignKey("skills.id", ondelete="CASCADE"), index=True)
    task_id: Mapped[int] = mapped_column(ForeignKey("tasks.id"))
    amount: Mapped[int] = mapped_column()
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())

    __table_args__ = (
        UniqueConstraint(
            "task_id", "user_id", "skill_id", name="uq_experience_logs_task_user_skill"
        ),
        CheckConstraint("amount > 0", name="ck_experience_logs_amount_positive"),
    )
