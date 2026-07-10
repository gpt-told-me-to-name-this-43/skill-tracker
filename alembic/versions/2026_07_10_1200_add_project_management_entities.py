from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "39be578ef3d2"
down_revision: str | Sequence[str] | None = "9d9a55c4552a"
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
    op.add_column("users", sa.Column("avatar_url", sa.String(length=500), nullable=True))
    op.add_column("users", sa.Column("position", sa.String(length=120), nullable=True))
    op.add_column(
        "users",
        sa.Column("member_status", sa.String(length=20), server_default="active", nullable=True),
    )
    op.execute("UPDATE users SET member_status = 'active' WHERE member_status IS NULL")
    op.alter_column("users", "member_status", nullable=False)
    op.create_check_constraint(
        "ck_users_member_status",
        "users",
        "member_status IN ('active', 'away', 'inactive')",
    )

    op.create_table(
        "labels",
        sa.Column("name", sa.String(length=80), nullable=False),
        sa.Column("color", sa.String(length=7), nullable=True),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.execute("CREATE UNIQUE INDEX uq_labels_name_lower ON labels (lower(name))")

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

    op.create_table(
        "teams",
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("description", sa.String(length=500), nullable=True),
        sa.Column("lead_id", sa.Integer(), nullable=True),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["lead_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.execute("CREATE UNIQUE INDEX uq_teams_name_lower ON teams (lower(name))")

    op.create_table(
        "team_members",
        sa.Column("team_id", sa.Integer(), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["team_id"], ["teams.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("team_id", "user_id", name="uq_team_member_pair"),
        sa.UniqueConstraint("user_id", name="uq_team_member_user"),
    )
    op.create_index(op.f("ix_team_members_team_id"), "team_members", ["team_id"])
    op.create_index(op.f("ix_team_members_user_id"), "team_members", ["user_id"])

    for label_name in INITIAL_LABELS:
        op.execute(
            sa.text(
                "INSERT INTO labels (name, color) VALUES (:name, NULL) "
                "ON CONFLICT DO NOTHING"
            ).bindparams(name=label_name)
        )


def downgrade() -> None:
    op.drop_index(op.f("ix_team_members_user_id"), table_name="team_members")
    op.drop_index(op.f("ix_team_members_team_id"), table_name="team_members")
    op.drop_table("team_members")
    op.execute("DROP INDEX uq_teams_name_lower")
    op.drop_table("teams")
    op.drop_index(op.f("ix_task_relations_right_task_id"), table_name="task_relations")
    op.drop_index(op.f("ix_task_relations_left_task_id"), table_name="task_relations")
    op.drop_table("task_relations")
    op.drop_index(op.f("ix_task_attachments_task_id"), table_name="task_attachments")
    op.drop_table("task_attachments")
    op.drop_index(op.f("ix_task_labels_label_id"), table_name="task_labels")
    op.drop_index(op.f("ix_task_labels_task_id"), table_name="task_labels")
    op.drop_table("task_labels")
    op.execute("DROP INDEX uq_labels_name_lower")
    op.drop_table("labels")
    op.drop_constraint("ck_users_member_status", "users", type_="check")
    op.drop_column("users", "member_status")
    op.drop_column("users", "position")
    op.drop_column("users", "avatar_url")
