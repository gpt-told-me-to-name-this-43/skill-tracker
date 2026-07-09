"""add task approval fields

Revision ID: 9d9a55c4552a
Revises: 0769f5a48beb
Create Date: 2026-07-08 21:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "9d9a55c4552a"
down_revision: str | Sequence[str] | None = "0769f5a48beb"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("approved_by_id", sa.Integer(), nullable=True))
    op.add_column("tasks", sa.Column("approved_at", sa.DateTime(), nullable=True))
    op.create_foreign_key(
        "fk_tasks_approved_by_id_users",
        "tasks",
        "users",
        ["approved_by_id"],
        ["id"],
    )
    op.create_index("ix_tasks_approved_by_id", "tasks", ["approved_by_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_tasks_approved_by_id", table_name="tasks")
    op.drop_constraint("fk_tasks_approved_by_id_users", "tasks", type_="foreignkey")
    op.drop_column("tasks", "approved_at")
    op.drop_column("tasks", "approved_by_id")
