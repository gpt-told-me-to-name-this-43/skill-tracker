from datetime import UTC, datetime, timedelta

import pytest

from app.models.enums import TaskStatus
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.task_repo import TaskRepository
from app.services.exceptions import NotFoundError
from app.services.task_lint_service import TaskLintService, lint_task_fields
from tests.conftest import make_task_clean, seed_task_fixtures

NOW = datetime(2026, 7, 13, 12, 0, 0)

CLEAN_FIELDS = {
    "title": "Implement kanban board",
    "description": "Add a kanban board with drag and drop support for tasks.",
    "status": TaskStatus.todo,
    "difficulty": 3,
    "deadline": NOW + timedelta(days=7),
    "assignee_id": 1,
    "label_count": 1,
    "exp_rewards": [50, 50],
    "now": NOW,
}


def lint(**overrides):
    return lint_task_fields(**{**CLEAN_FIELDS, **overrides})


def codes(**overrides) -> list[str]:
    return [warning.code for warning in lint(**overrides)]


def test_clean_task_has_no_warnings():
    assert lint() == []


@pytest.mark.parametrize(
    ("title", "expected"),
    [
        ("Fix log", ["title_too_short"]),
        ("Fix logs", []),
        ("  Fix log  ", ["title_too_short"]),
        ("Refactoring", ["title_not_descriptive"]),
        ("Fix login", []),
    ],
)
def test_title_rules(title, expected):
    assert codes(title=title) == expected


def test_short_one_word_title_reports_only_length():
    assert codes(title="Fix") == ["title_too_short"]


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        (None, ["description_missing"]),
        ("", ["description_missing"]),
        ("   ", ["description_missing"]),
        ("a" * 29, ["description_too_short"]),
        ("a" * 30, []),
    ],
)
def test_description_rules(description, expected):
    assert codes(description=description) == expected


def test_no_skill_rewards_warns():
    assert codes(exp_rewards=[]) == ["no_skill_rewards"]


@pytest.mark.parametrize(
    ("deadline", "expected"),
    [
        (None, ["no_deadline"]),
        (NOW - timedelta(minutes=1), ["deadline_past"]),
        (NOW + timedelta(hours=23), ["deadline_soon"]),
        (NOW + timedelta(hours=25), []),
    ],
)
def test_deadline_rules(deadline, expected):
    assert codes(deadline=deadline) == expected


def test_done_task_skips_deadline_rules():
    assert codes(status=TaskStatus.done, deadline=NOW - timedelta(days=1)) == []
    assert codes(status=TaskStatus.done, deadline=None) == []


def test_aware_deadline_is_normalized_to_naive_utc():
    aware_past = (NOW - timedelta(minutes=1)).replace(tzinfo=UTC)
    assert codes(deadline=aware_past) == ["deadline_past"]


def test_in_progress_without_assignee_warns():
    assert codes(status=TaskStatus.in_progress, assignee_id=None) == [
        "in_progress_without_assignee"
    ]
    assert codes(status=TaskStatus.in_progress, assignee_id=1) == []
    assert codes(status=TaskStatus.todo, assignee_id=None) == []


@pytest.mark.parametrize(
    ("exp_rewards", "expected"),
    [
        ([29], ["xp_below_difficulty"]),
        ([30], []),
        ([300], []),
        ([301], ["xp_above_difficulty"]),
        ([250, 251], ["xp_above_difficulty"]),
        ([500], ["xp_above_difficulty"]),
        ([501], ["xp_above_difficulty", "suspicious_reward"]),
    ],
)
def test_xp_vs_difficulty_rules(exp_rewards, expected):
    assert codes(difficulty=3, exp_rewards=exp_rewards) == expected


def test_no_labels_is_info():
    warnings = lint(label_count=0)
    assert [warning.code for warning in warnings] == ["no_labels"]
    assert warnings[0].severity == "info"


def test_warnings_carry_severity_and_field():
    warnings = lint(title="Fix", exp_rewards=[])
    by_code = {warning.code: warning for warning in warnings}
    assert by_code["title_too_short"].severity == "warning"
    assert by_code["title_too_short"].field == "title"
    assert by_code["no_skill_rewards"].field == "skills"


def build_lint_service(session) -> TaskLintService:
    return TaskLintService(
        task_repo=TaskRepository(session),
        experience_repo=ExperienceRepository(session),
    )


async def test_lint_task_unknown_id_raises_not_found(db_session):
    service = build_lint_service(db_session)

    with pytest.raises(NotFoundError):
        await service.lint_task(9999)


async def test_lint_task_reports_seeded_sparse_task(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_lint_service(db_session)

    report = await service.lint_task(fixtures.task.id)

    assert report.task_id == fixtures.task.id
    assert [warning.code for warning in report.warnings] == [
        "description_missing",
        "no_skill_rewards",
        "no_deadline",
        "no_labels",
    ]


async def test_lint_task_uses_labels_and_skill_rewards(db_session):
    fixtures = await seed_task_fixtures(db_session)
    await make_task_clean(db_session, fixtures)

    service = build_lint_service(db_session)
    report = await service.lint_task(fixtures.task.id)

    assert report.warnings == []


async def test_lint_task_does_not_write(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_lint_service(db_session)

    await service.lint_task(fixtures.task.id)

    assert not db_session.new
    assert not db_session.dirty
