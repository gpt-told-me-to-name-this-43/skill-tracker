"""add_user_skills_constraints_and_remove_level

Revision ID: 4ad7d0934cc5
Revises: f60470687a86
Create Date: 2026-07-03 17:09:58.531820

"""
from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op


# revision identifiers, used by Alembic.
revision: str = '4ad7d0934cc5'
down_revision: str | Sequence[str] | None = 'f60470687a86'
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
    # 1. Удаляем колонку level из user_skills
    op.drop_column('user_skills', 'level')

    # 2. Добавляем timestamps в user_skills
    op.add_column(
        'user_skills',
        sa.Column('created_at', sa.DateTime(), server_default=sa.text('now()'), nullable=True),
    )
    op.add_column(
        'user_skills',
        sa.Column('updated_at', sa.DateTime(), server_default=sa.text('now()'), nullable=True),
    )

    # 3. Добавляем timestamps в skills
    op.add_column(
        'skills',
        sa.Column('created_at', sa.DateTime(), server_default=sa.text('now()'), nullable=True),
    )
    op.add_column(
        'skills',
        sa.Column('updated_at', sa.DateTime(), server_default=sa.text('now()'), nullable=True),
    )

    # 4. Создаём unique index на (user_id, skill_id)
    op.create_index(
        'ix_user_skills_user_id_skill_id',
        'user_skills',
        ['user_id', 'skill_id'],
        unique=True,
    )

    # 5. Создаём case-insensitive unique index для skills.name
    op.create_index(
        'ix_skills_name_lower',
        'skills',
        [sa.text('lower(name)')],
        unique=True,
    )


def downgrade() -> None:
    op.drop_index('ix_skills_name_lower', table_name='skills')
    op.drop_index('ix_user_skills_user_id_skill_id', table_name='user_skills')

    op.drop_column('skills', 'updated_at')
    op.drop_column('skills', 'created_at')
    op.drop_column('user_skills', 'updated_at')
    op.drop_column('user_skills', 'created_at')

    op.add_column('user_skills', sa.Column('level', sa.Integer(), nullable=True))
    op.execute("UPDATE user_skills SET level = 1 WHERE level IS NULL")
    op.alter_column('user_skills', 'level', nullable=False)