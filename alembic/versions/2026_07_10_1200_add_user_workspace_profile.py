"""add user workspace profile fields

Revision ID: e7b6c9d2a4f1
Revises: 9d9a55c4552a
Create Date: 2026-07-10 12:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "e7b6c9d2a4f1"
down_revision: str | Sequence[str] | None = "9d9a55c4552a"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("users", sa.Column("avatar_url", sa.String(length=2048), nullable=True))
    op.add_column("users", sa.Column("position", sa.String(length=100), nullable=True))
    op.add_column(
        "users",
        sa.Column(
            "member_status",
            sa.String(length=20),
            server_default="active",
            nullable=True,
        ),
    )
    op.execute("UPDATE users SET member_status = 'active' WHERE member_status IS NULL")
    op.alter_column("users", "member_status", nullable=False, server_default="active")


def downgrade() -> None:
    op.drop_column("users", "member_status")
    op.drop_column("users", "position")
    op.drop_column("users", "avatar_url")
