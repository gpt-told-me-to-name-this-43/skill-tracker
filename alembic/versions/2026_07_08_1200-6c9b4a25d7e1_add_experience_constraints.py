"""add experience constraints

Revision ID: 6c9b4a25d7e1
Revises: 1a5c0ec5f98f
Create Date: 2026-07-08 12:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "6c9b4a25d7e1"
down_revision: str | Sequence[str] | None = "1a5c0ec5f98f"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "task_skills",
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
    )
    op.create_check_constraint(
        "ck_task_skills_exp_reward_positive",
        "task_skills",
        "exp_reward > 0",
    )

    op.alter_column(
        "experience_logs",
        "task_id",
        existing_type=sa.Integer(),
        nullable=False,
    )
    op.drop_constraint("experience_logs_task_id_fkey", "experience_logs", type_="foreignkey")
    op.create_foreign_key(
        "experience_logs_task_id_fkey",
        "experience_logs",
        "tasks",
        ["task_id"],
        ["id"],
    )
    op.create_check_constraint(
        "ck_experience_logs_amount_positive",
        "experience_logs",
        "amount > 0",
    )
    op.create_unique_constraint(
        "uq_experience_logs_task_user_skill",
        "experience_logs",
        ["task_id", "user_id", "skill_id"],
    )


def downgrade() -> None:
    op.drop_constraint("uq_experience_logs_task_user_skill", "experience_logs", type_="unique")
    op.drop_constraint("ck_experience_logs_amount_positive", "experience_logs", type_="check")
    op.drop_constraint("experience_logs_task_id_fkey", "experience_logs", type_="foreignkey")
    op.create_foreign_key(
        "experience_logs_task_id_fkey",
        "experience_logs",
        "tasks",
        ["task_id"],
        ["id"],
        ondelete="SET NULL",
    )
    op.alter_column(
        "experience_logs",
        "task_id",
        existing_type=sa.Integer(),
        nullable=True,
    )

    op.drop_constraint("ck_task_skills_exp_reward_positive", "task_skills", type_="check")
    op.drop_column("task_skills", "created_at")
