from datetime import UTC, datetime, timedelta, timezone

from app.schemas.task import TaskCreate
from tests.conftest import build_task_service, seed_task_fixtures


async def test_create_task_stores_aware_deadline_as_naive_utc(db_session):
    """Дедлайн с не-UTC смещением сохраняется как naive UTC, а не как локальное время."""
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    deadline_utc = datetime.now(UTC) + timedelta(days=2)
    deadline_local = deadline_utc.astimezone(timezone(timedelta(hours=5)))

    created = await service.create_task(
        TaskCreate(title="Aware deadline task", deadline=deadline_local),
        creator_id=fixtures.creator.id,
    )

    assert created["deadline"] == deadline_utc.replace(tzinfo=None)
