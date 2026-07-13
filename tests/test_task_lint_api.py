import pytest

from app.api.deps import get_task_lint_service
from app.main import app
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.task_repo import TaskRepository
from app.services.task_lint_service import TaskLintService
from tests.conftest import make_task_clean, seed_task_fixtures


@pytest.fixture
def lint_service_override(db_session):
    service = TaskLintService(
        task_repo=TaskRepository(db_session),
        experience_repo=ExperienceRepository(db_session),
    )

    async def override_get_task_lint_service():
        return service

    app.dependency_overrides[get_task_lint_service] = override_get_task_lint_service
    yield
    app.dependency_overrides.pop(get_task_lint_service, None)


async def test_lint_sparse_task_returns_warnings(client, db_session, lint_service_override):
    fixtures = await seed_task_fixtures(db_session)

    resp = await client.get(f"/api/v1/tasks/{fixtures.task.id}/lint")

    assert resp.status_code == 200
    body = resp.json()
    assert body["task_id"] == fixtures.task.id
    assert [warning["code"] for warning in body["warnings"]] == [
        "description_missing",
        "no_skill_rewards",
        "no_deadline",
        "no_labels",
    ]
    first = body["warnings"][0]
    assert set(first) == {"code", "field", "severity", "message"}


async def test_lint_clean_task_returns_empty_list(client, db_session, lint_service_override):
    fixtures = await seed_task_fixtures(db_session)
    await make_task_clean(db_session, fixtures)

    resp = await client.get(f"/api/v1/tasks/{fixtures.task.id}/lint")

    assert resp.status_code == 200
    assert resp.json() == {"task_id": fixtures.task.id, "warnings": []}


async def test_lint_unknown_task_returns_404(client, lint_service_override):
    resp = await client.get("/api/v1/tasks/999999/lint")

    assert resp.status_code == 404
    assert "error" in resp.json()


async def test_lint_does_not_require_authentication(client, db_session, lint_service_override):
    """The endpoint is public, like the sibling task GETs."""
    fixtures = await seed_task_fixtures(db_session)

    resp = await client.get(
        f"/api/v1/tasks/{fixtures.task.id}/lint",
        headers={"Authorization": ""},
    )

    assert resp.status_code == 200
