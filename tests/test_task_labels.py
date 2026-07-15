import pytest
from pydantic import ValidationError
from sqlalchemy import func, select

from app.models import ExperienceLog, Label, TaskLabel
from app.schemas.label import LabelCreate, TaskLabelsSet
from app.services.exceptions import ConflictError, NotFoundError
from tests.conftest import build_task_service, seed_task_fixtures


async def test_create_label_trims_name_and_persists(db_session):
    service = build_task_service(db_session)

    label = await service.create_label(LabelCreate(name="  Feature  ", color="#3B82F6"))

    assert label.name == "Feature"
    assert label.color == "#3B82F6"
    stored = await db_session.scalar(select(Label).where(Label.id == label.id))
    assert stored.name == "Feature"


async def test_create_label_rejects_duplicate_name_case_insensitively(db_session):
    service = build_task_service(db_session)
    await service.create_label(LabelCreate(name="Feature"))

    with pytest.raises(ConflictError):
        await service.create_label(LabelCreate(name="fEaTuRe"))


async def test_create_label_treats_wildcard_characters_literally(db_session):
    """Regression: the uniqueness lookup must not interpret the new name as a LIKE pattern."""
    service = build_task_service(db_session)

    # "_" is a single-character LIKE wildcard, so "a_c" would falsely match the existing "abc".
    await service.create_label(LabelCreate(name="abc"))
    created = await service.create_label(LabelCreate(name="a_c"))
    assert created.name == "a_c"

    # "%" matches any run of characters, so "Cov%" would falsely match the existing "Coverage".
    await service.create_label(LabelCreate(name="Coverage"))
    created = await service.create_label(LabelCreate(name="Cov%"))
    assert created.name == "Cov%"

    # A genuine case-insensitive collision is still reported.
    with pytest.raises(ConflictError):
        await service.create_label(LabelCreate(name="cov%"))


async def test_set_task_labels_replaces_the_whole_set(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    detail = await service.set_task_labels(fixtures.task.id, [fixtures.backend_label.id])
    assert [label["name"] for label in detail["labels"]] == ["Backend"]

    detail = await service.set_task_labels(fixtures.task.id, [fixtures.frontend_label.id])
    assert [label["name"] for label in detail["labels"]] == ["Frontend"]

    rows = (
        await db_session.scalars(select(TaskLabel).where(TaskLabel.task_id == fixtures.task.id))
    ).all()
    assert len(rows) == 1


async def test_set_task_labels_with_empty_list_clears_labels(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await service.set_task_labels(
        fixtures.task.id,
        [fixtures.backend_label.id, fixtures.frontend_label.id],
    )

    detail = await service.set_task_labels(fixtures.task.id, [])

    assert detail["labels"] == []
    remaining = await db_session.scalar(
        select(func.count()).select_from(TaskLabel).where(TaskLabel.task_id == fixtures.task.id)
    )
    assert remaining == 0


async def test_set_task_labels_rejects_unknown_label(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    with pytest.raises(NotFoundError):
        await service.set_task_labels(fixtures.task.id, [fixtures.backend_label.id, 9999])


async def test_set_task_labels_rejects_unknown_task(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    with pytest.raises(NotFoundError):
        await service.set_task_labels(9999, [fixtures.backend_label.id])


def test_task_labels_set_rejects_duplicate_ids():
    with pytest.raises(ValidationError):
        TaskLabelsSet(label_ids=[1, 1, 3])


@pytest.mark.parametrize("color", ["red", "#FFF", "#GGGGGG", "3B82F6"])
def test_label_create_rejects_invalid_color(color):
    with pytest.raises(ValidationError):
        LabelCreate(name="Feature", color=color)


def test_label_create_rejects_blank_name():
    with pytest.raises(ValidationError):
        LabelCreate(name="   ")


async def test_labels_never_award_experience(db_session):
    """D1: Label and Skill are distinct. Labels must not touch the XP loop."""
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    await service.set_task_labels(
        fixtures.task.id,
        [fixtures.backend_label.id, fixtures.frontend_label.id],
    )
    await service.set_task_labels(fixtures.task.id, [])

    logs = await db_session.scalar(select(func.count()).select_from(ExperienceLog))
    assert logs == 0
