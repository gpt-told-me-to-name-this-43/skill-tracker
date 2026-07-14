"""add github import fields

Revision ID: c9d1e4f7a2b5
Revises: 39be578ef3d2
Create Date: 2026-07-12 10:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "c9d1e4f7a2b5"
down_revision: str | Sequence[str] | None = "39be578ef3d2"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("users", sa.Column("github_login", sa.String(length=100), nullable=True))
    op.create_index("ix_users_github_login", "users", ["github_login"], unique=True)
    op.add_column(
        "users",
        sa.Column(
            "is_placeholder",
            sa.Boolean(),
            nullable=False,
            server_default=sa.text("false"),
        ),
    )

    op.add_column("tasks", sa.Column("github_issue_number", sa.Integer(), nullable=True))
    op.create_index(
        "ix_tasks_github_issue_number",
        "tasks",
        ["github_issue_number"],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index("ix_tasks_github_issue_number", table_name="tasks")
    op.drop_column("tasks", "github_issue_number")

    op.drop_column("users", "is_placeholder")
    op.drop_index("ix_users_github_login", table_name="users")
    op.drop_column("users", "github_login")
