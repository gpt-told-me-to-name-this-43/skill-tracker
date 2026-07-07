"""add task indexes

Revision ID: 1a5c0ec5f98f
Revises: 4ad7d0934cc5
Create Date: 2026-07-07 11:04:09.807881

"""

from collections.abc import Sequence

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "1a5c0ec5f98f"
down_revision: str | Sequence[str] | None = "4ad7d0934cc5"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_index("ix_tasks_status", "tasks", ["status"], unique=False)
    op.create_index("ix_tasks_assignee_id", "tasks", ["assignee_id"], unique=False)
    op.create_index("ix_tasks_difficulty", "tasks", ["difficulty"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_tasks_difficulty", table_name="tasks")
    op.drop_index("ix_tasks_assignee_id", table_name="tasks")
    op.drop_index("ix_tasks_status", table_name="tasks")
