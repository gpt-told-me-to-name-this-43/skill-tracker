import pytest

from app.api.deps import get_current_user, get_task_service
from app.main import app
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.services.experience import DefaultExperienceAwarder
from app.services.task_service import TaskService
from tests.conftest import seed_task_fixtures


@pytest.fixture
def task_service_override(db_session):
    service = TaskService(
        task_repo=TaskRepository(db_session),
        user_repo=UserRepository(db_session),
        experience_awarder=DefaultExperienceAwarder(ExperienceRepository(db_session)),
    )

    async def override_get_task_service():
        return service

    app.dependency_overrides[get_task_service] = override_get_task_service
    yield
    app.dependency_overrides.pop(get_task_service, None)


async def _login_as(db_session, user_id: int):
    user_repo = UserRepository(db_session)
    user = await user_repo.get_user_by_id(user_id)

    async def override_get_current_user():
        return user

    app.dependency_overrides[get_current_user] = override_get_current_user


async def test_create_task_sets_creator_and_default_status(
    client, db_session, task_service_override
):
    fixtures = await seed_task_fixtures(db_session)
    await _login_as(db_session, fixtures.creator.id)

    resp = await client.post("/api/v1/tasks", json={"title": "New task"})

    assert resp.status_code == 201
    body = resp.json()
    assert body["creator_id"] == fixtures.creator.id
    assert body["status"] == "todo"
    assert body["assignee_id"] is None


async def test_create_task_past_deadline_returns_400(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)
    await _login_as(db_session, fixtures.creator.id)

    resp = await client.post(
        "/api/v1/tasks",
        json={"title": "Late", "deadline": "2000-01-01T00:00:00Z"},
    )

    assert resp.status_code == 400


async def test_create_task_difficulty_out_of_range_returns_422(
    client, db_session, task_service_override
):
    fixtures = await seed_task_fixtures(db_session)
    await _login_as(db_session, fixtures.creator.id)

    for difficulty in (0, 6):
        resp = await client.post("/api/v1/tasks", json={"title": "T", "difficulty": difficulty})
        assert resp.status_code == 422


async def test_patch_task_ignores_status_and_assignee(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)

    resp = await client.patch(
        f"/api/v1/tasks/{fixtures.other_task.id}",
        json={"title": "Renamed", "status": "done", "assignee_id": fixtures.assignee.id},
    )

    assert resp.status_code == 200
    body = resp.json()
    assert body["title"] == "Renamed"
    assert body["status"] == "todo"
    assert body["assignee_id"] is None


async def test_done_is_gated_by_review_and_approval(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)
    await _login_as(db_session, fixtures.creator.id)
    task_id = fixtures.task.id

    resp = await client.patch(f"/api/v1/tasks/{task_id}/status", json={"status": "done"})
    assert resp.status_code == 400

    for status in ("in_progress", "review"):
        resp = await client.patch(f"/api/v1/tasks/{task_id}/status", json={"status": status})
        assert resp.status_code == 200
        assert resp.json()["status"] == status

    resp = await client.patch(f"/api/v1/tasks/{task_id}/status", json={"status": "done"})
    assert resp.status_code == 400

    resp = await client.patch(f"/api/v1/tasks/{task_id}/approve")
    assert resp.status_code == 200
    assert resp.json()["approved_by_id"] == fixtures.creator.id

    resp = await client.patch(f"/api/v1/tasks/{task_id}/status", json={"status": "done"})
    assert resp.status_code == 200
    assert resp.json()["status"] == "done"


async def test_change_status_invalid_value_returns_422(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)

    resp = await client.patch(
        f"/api/v1/tasks/{fixtures.task.id}/status", json={"status": "invalid"}
    )

    assert resp.status_code == 422


async def test_change_status_missing_task_returns_404(client, db_session, task_service_override):
    await seed_task_fixtures(db_session)

    resp = await client.patch("/api/v1/tasks/999999/status", json={"status": "review"})

    assert resp.status_code == 404


async def test_assign_and_unassign(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)
    task_id = fixtures.other_task.id

    resp = await client.patch(
        f"/api/v1/tasks/{task_id}/assign", json={"assignee_id": fixtures.assignee.id}
    )
    assert resp.status_code == 200
    assert resp.json()["assignee_id"] == fixtures.assignee.id

    resp = await client.patch(f"/api/v1/tasks/{task_id}/assign", json={"assignee_id": None})
    assert resp.status_code == 200
    assert resp.json()["assignee_id"] is None


async def test_assign_missing_user_returns_404(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)

    resp = await client.patch(
        f"/api/v1/tasks/{fixtures.task.id}/assign", json={"assignee_id": 999999}
    )

    assert resp.status_code == 404


async def test_list_tasks_filters_by_status(client, db_session, task_service_override):
    fixtures = await seed_task_fixtures(db_session)

    resp = await client.patch(
        f"/api/v1/tasks/{fixtures.task.id}/status", json={"status": "in_progress"}
    )
    assert resp.status_code == 200

    resp = await client.get("/api/v1/tasks?status=in_progress")
    assert resp.status_code == 200
    body = resp.json()
    assert [task["id"] for task in body] == [fixtures.task.id]

    resp = await client.get("/api/v1/tasks?status=done")
    assert resp.status_code == 200
    assert resp.json() == []
