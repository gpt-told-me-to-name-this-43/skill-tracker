import pytest
from pydantic import ValidationError
from sqlalchemy import func, select, text

from app.models import TaskRelation
from app.schemas.task import TaskRelatedSet
from app.services.exceptions import BadRequestError, NotFoundError
from tests.conftest import build_task_service, seed_task_fixtures


async def _relation_pairs(db_session) -> list[tuple[int, int]]:
    rows = (await db_session.scalars(select(TaskRelation))).all()
    return sorted((row.left_task_id, row.right_task_id) for row in rows)


async def test_set_related_tasks_normalizes_pair_order(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    low, high = sorted([fixtures.task.id, fixtures.third_task.id])

    # Pass the ids in descending order to prove the service normalizes rather than stores as given.
    await service.set_related_tasks(high, [low])

    assert await _relation_pairs(db_session) == [(low, high)]


async def test_related_tasks_are_bidirectional(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    await service.set_related_tasks(fixtures.task.id, [fixtures.other_task.id])

    from_other_side = await service.list_related_tasks(fixtures.other_task.id)
    assert [item["id"] for item in from_other_side] == [fixtures.task.id]
    assert from_other_side[0]["title"] == "Add Kanban"
    assert from_other_side[0]["status"].value == "todo"

    detail = await service.get_task_by_id(fixtures.other_task.id)
    assert [item["id"] for item in detail["related_tasks"]] == [fixtures.task.id]
    assert detail["related_tasks_count"] == 1


async def test_set_related_tasks_replaces_the_whole_set(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    await service.set_related_tasks(
        fixtures.task.id,
        [fixtures.other_task.id, fixtures.third_task.id],
    )
    assert len(await service.list_related_tasks(fixtures.task.id)) == 2

    related = await service.set_related_tasks(fixtures.task.id, [fixtures.third_task.id])

    assert [item["id"] for item in related] == [fixtures.third_task.id]


async def test_empty_task_ids_clears_only_that_tasks_relations(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await service.set_related_tasks(fixtures.task.id, [fixtures.other_task.id])
    await service.set_related_tasks(fixtures.other_task.id, [fixtures.third_task.id])

    # other_task now relates to both task and third_task. Clearing `task` must leave the
    # other_task <-> third_task pair untouched.
    assert await service.set_related_tasks(fixtures.task.id, []) == []

    surviving = sorted([fixtures.other_task.id, fixtures.third_task.id])
    assert await _relation_pairs(db_session) == [tuple(surviving)]


async def test_task_cannot_be_related_to_itself(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    with pytest.raises(BadRequestError):
        await service.set_related_tasks(fixtures.task.id, [fixtures.task.id])


async def test_set_related_tasks_rejects_unknown_task(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    with pytest.raises(NotFoundError):
        await service.set_related_tasks(fixtures.task.id, [fixtures.other_task.id, 9999])

    assert await _relation_pairs(db_session) == []


async def test_relations_are_not_duplicated_when_set_from_both_sides(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    await service.set_related_tasks(fixtures.task.id, [fixtures.other_task.id])
    await service.set_related_tasks(fixtures.other_task.id, [fixtures.task.id])

    total = await db_session.scalar(select(func.count()).select_from(TaskRelation))
    assert total == 1


async def test_deleting_a_task_cascades_relations_on_both_sides(db_session):
    """Both TaskRelation FKs use ON DELETE CASCADE, so no broken rows may survive."""
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await service.set_related_tasks(
        fixtures.other_task.id,
        [fixtures.task.id, fixtures.third_task.id],
    )
    assert len(await _relation_pairs(db_session)) == 2

    # other_task sits on the left of one pair and on the right of the other.
    await db_session.execute(
        text("DELETE FROM tasks WHERE id = :id"),
        {"id": fixtures.other_task.id},
    )

    assert await _relation_pairs(db_session) == []


def test_task_related_set_rejects_duplicate_ids():
    with pytest.raises(ValidationError):
        TaskRelatedSet(task_ids=[15, 18, 15])


async def test_task_list_item_exposes_counts_and_hides_email(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await service.set_related_tasks(fixtures.task.id, [fixtures.other_task.id])
    await service.set_task_labels(fixtures.task.id, [fixtures.backend_label.id])

    tasks = await service.get_tasks()
    by_id = {item["id"]: item for item in tasks}

    assigned = by_id[fixtures.task.id]
    assert assigned["related_tasks_count"] == 1
    assert assigned["attachments_count"] == 0
    assert [label["name"] for label in assigned["labels"]] == ["Backend"]
    assert assigned["assignee"]["username"] == "developer"
    assert "email" not in assigned["assignee"]
    assert "email" not in assigned["creator"]

    unassigned = by_id[fixtures.third_task.id]
    assert unassigned["assignee"] is None
    assert unassigned["related_tasks_count"] == 0
