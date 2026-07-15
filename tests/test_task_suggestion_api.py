"""Интеграционные тесты POST /api/v1/tasks/analyze."""

from app.api.deps import get_current_user, get_task_suggestion_service
from app.integrations.openrouter_client import RawTaskSuggestion
from app.main import app
from app.models import Label, Skill
from app.models.enums import MemberStatus
from app.models.user import User
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.services.task_suggestion_service import TaskSuggestionService
from tests.test_task_suggestion_service import FakeSuggestionSource


async def _override_current_user() -> User:
    user = User(
        username="analyst",
        email="analyst@example.com",
        hashed_password="hashed",
        role="user",
        member_status=MemberStatus.active.value,
    )
    user.id = 42
    return user


def _override_service(db_session, source) -> None:
    service = TaskSuggestionService(
        task_repo=TaskRepository(db_session),
        skill_repo=SkillRepository(db_session),
        source=source,
    )

    async def override() -> TaskSuggestionService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_task_suggestion_service] = override


async def test_analyze_returns_suggestion_contract(client, db_session):
    label = Label(name="Backend", color="#3B82F6")
    skill = Skill(name="Python", description="Backend language")
    db_session.add_all([label, skill])
    await db_session.flush()

    source = FakeSuggestionSource(
        RawTaskSuggestion(
            difficulty=4,
            estimated_days=5,
            label_ids=[label.id],
            skills=[(skill.id, 150)],
        )
    )
    _override_service(db_session, source)

    resp = await client.post(
        "/api/v1/tasks/analyze",
        json={"title": "Fix login bug", "description": "Users cannot log in"},
    )

    assert resp.status_code == 200
    body = resp.json()
    assert body["difficulty"] == 4
    assert body["deadline"] is not None
    assert [item["id"] for item in body["labels"]] == [label.id]
    assert body["labels"][0]["name"] == "Backend"
    assert body["skills"] == [
        {
            "skill": {"id": skill.id, "name": "Python", "description": "Backend language"},
            "exp_reward": 150,
        }
    ]


async def test_analyze_requires_authentication(client):
    resp = await client.post("/api/v1/tasks/analyze", json={"title": "Fix login bug"})

    assert resp.status_code == 401


async def test_analyze_empty_title_returns_422(client, db_session):
    _override_service(db_session, FakeSuggestionSource(RawTaskSuggestion(difficulty=3)))

    resp = await client.post("/api/v1/tasks/analyze", json={"title": "   "})

    assert resp.status_code == 422


async def test_analyze_without_configured_source_returns_503(client, db_session):
    _override_service(db_session, source=None)

    resp = await client.post("/api/v1/tasks/analyze", json={"title": "Fix login bug"})

    assert resp.status_code == 503
    assert "error" in resp.json()
