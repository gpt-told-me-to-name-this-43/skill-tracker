"""add task labels, attachments and relations

Revision ID: 39be578ef3d2
Revises: b4a8d2c7f1e3
Create Date: 2026-07-10 14:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "39be578ef3d2"
down_revision: str | Sequence[str] | None = "b4a8d2c7f1e3"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

INITIAL_LABELS = (
    "Backend",
    "Frontend",
    "DevOps",
    "Design",
    "QA",
    "Documentation",
    "Bug",
    "Feature",
    "Enhancement",
)


def upgrade() -> None:
    op.create_table(
        "labels",
        sa.Column("name", sa.String(length=80), nullable=False),
        sa.Column("color", sa.String(length=7), nullable=True),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("uq_labels_name_lower", "labels", [sa.text("lower(name)")], unique=True)

    op.create_table(
        "task_labels",
        sa.Column("task_id", sa.Integer(), nullable=False),
        sa.Column("label_id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["label_id"], ["labels.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("task_id", "label_id", name="uq_task_label"),
    )
    op.create_index(op.f("ix_task_labels_task_id"), "task_labels", ["task_id"])
    op.create_index(op.f("ix_task_labels_label_id"), "task_labels", ["label_id"])

    op.create_table(
        "task_attachments",
        sa.Column("task_id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("url", sa.String(length=1000), nullable=False),
        sa.Column("created_by_id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["created_by_id"], ["users.id"]),
        sa.ForeignKeyConstraint(["task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(op.f("ix_task_attachments_task_id"), "task_attachments", ["task_id"])

    op.create_table(
        "task_relations",
        sa.Column("left_task_id", sa.Integer(), nullable=False),
        sa.Column("right_task_id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.CheckConstraint("left_task_id < right_task_id", name="ck_task_relation_order"),
        sa.ForeignKeyConstraint(["left_task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["right_task_id"], ["tasks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("left_task_id", "right_task_id", name="uq_task_relation"),
    )
    op.create_index(op.f("ix_task_relations_left_task_id"), "task_relations", ["left_task_id"])
    op.create_index(op.f("ix_task_relations_right_task_id"), "task_relations", ["right_task_id"])

    for label_name in INITIAL_LABELS:
        op.execute(
            sa.text(
                "INSERT INTO labels (name, color) VALUES (:name, NULL) ON CONFLICT DO NOTHING"
            ).bindparams(name=label_name)
        )


def downgrade() -> None:
    op.drop_index(op.f("ix_task_relations_right_task_id"), table_name="task_relations")
    op.drop_index(op.f("ix_task_relations_left_task_id"), table_name="task_relations")
    op.drop_table("task_relations")
    op.drop_index(op.f("ix_task_attachments_task_id"), table_name="task_attachments")
    op.drop_table("task_attachments")
    op.drop_index(op.f("ix_task_labels_label_id"), table_name="task_labels")
    op.drop_index(op.f("ix_task_labels_task_id"), table_name="task_labels")
    op.drop_table("task_labels")
    op.drop_index("uq_labels_name_lower", table_name="labels")
    op.drop_table("labels")
