"""baseline schema

Revision ID: f60470687a86
Revises:
Create Date: 2026-07-01 15:29:49.456169

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "f60470687a86"
down_revision: str | Sequence[str] | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


task_status = postgresql.ENUM(
    "todo",
    "in_progress",
    "review",
    "done",
    name="taskstatus",
    create_type=False,
)


def upgrade() -> None:
    """Upgrade schema."""
    task_status.create(op.get_bind(), checkfirst=True)

    op.create_table(
        "skills",
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_skills_name"), "skills", ["name"], unique=True)

    op.create_table(
        "users",
        sa.Column("username", sa.String(length=50), nullable=False),
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("hashed_password", sa.String(length=255), nullable=False),
        sa.Column("role", sa.String(length=20), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_users_email"), "users", ["email"], unique=True)
    op.create_index(op.f("ix_users_username"), "users", ["username"], unique=True)

    op.create_table(
        "tasks",
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("status", task_status, nullable=False),
        sa.Column("difficulty", sa.SmallInteger(), nullable=False),
        sa.Column("deadline", sa.DateTime(), nullable=True),
        sa.Column("creator_id", sa.Integer(), nullable=False),
        sa.Column("assignee_id", sa.Integer(), nullable=True),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["assignee_id"], ["users.id"]),
        sa.ForeignKeyConstraint(["creator_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
    )

    op.create_table(
        "user_skills",
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("skill_id", sa.Integer(), nullable=False),
        sa.Column("experience", sa.Integer(), nullable=False),
        sa.Column("level", sa.Integer(), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["skill_id"], ["skills.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("user_id", "skill_id", name="uq_user_skill"),
    )
    op.create_index(op.f("ix_user_skills_skill_id"), "user_skills", ["skill_id"], unique=False)
    op.create_index(op.f("ix_user_skills_user_id"), "user_skills", ["user_id"], unique=False)

    op.create_table(
        "experience_logs",
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("skill_id", sa.Integer(), nullable=False),
        sa.Column("task_id", sa.Integer(), nullable=True),
        sa.Column("amount", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["skill_id"], ["skills.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["task_id"], ["tasks.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        op.f("ix_experience_logs_skill_id"), "experience_logs", ["skill_id"], unique=False
    )
    op.create_index(
        op.f("ix_experience_logs_user_id"), "experience_logs", ["user_id"], unique=False
    )

    op.create_table(
        "task_skills",
        sa.Column("task_id", sa.Integer(), nullable=False),
        sa.Column("skill_id", sa.Integer(), nullable=False),
        sa.Column("exp_reward", sa.Integer(), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["skill_id"], ["skills.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("task_id", "skill_id", name="uq_task_skill"),
    )
    op.create_index(op.f("ix_task_skills_skill_id"), "task_skills", ["skill_id"], unique=False)
    op.create_index(op.f("ix_task_skills_task_id"), "task_skills", ["task_id"], unique=False)


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index(op.f("ix_task_skills_task_id"), table_name="task_skills")
    op.drop_index(op.f("ix_task_skills_skill_id"), table_name="task_skills")
    op.drop_table("task_skills")

    op.drop_index(op.f("ix_experience_logs_user_id"), table_name="experience_logs")
    op.drop_index(op.f("ix_experience_logs_skill_id"), table_name="experience_logs")
    op.drop_table("experience_logs")

    op.drop_index(op.f("ix_user_skills_user_id"), table_name="user_skills")
    op.drop_index(op.f("ix_user_skills_skill_id"), table_name="user_skills")
    op.drop_table("user_skills")

    op.drop_table("tasks")

    op.drop_index(op.f("ix_users_username"), table_name="users")
    op.drop_index(op.f("ix_users_email"), table_name="users")
    op.drop_table("users")

    op.drop_index(op.f("ix_skills_name"), table_name="skills")
    op.drop_table("skills")

    task_status.drop(op.get_bind(), checkfirst=True)
