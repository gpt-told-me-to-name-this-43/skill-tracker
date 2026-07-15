"""change role default to user

Revision ID: 0769f5a48beb
Revises: 6c9b4a25d7e1
Create Date: 2026-07-08 19:43:56.170340

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = "0769f5a48beb"
down_revision: str | Sequence[str] | None = "6c9b4a25d7e1"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.execute("UPDATE users SET role = 'user' WHERE role = 'member'")
    # Меняем server_default в БД с 'member' на 'user'
    op.alter_column(
        "users",
        "role",
        server_default="user",
        existing_type=sa.String(20),
        existing_nullable=False,  # если колонка NOT NULL; иначе True
    )


def downgrade() -> None:
    op.execute("UPDATE users SET role = 'member' WHERE role = 'user'")
    # Возвращаем всё как было
    op.alter_column(
        "users",
        "role",
        server_default="member",
        existing_type=sa.String(20),
        existing_nullable=False,
    )
