from datetime import UTC, datetime

import pytest

from app.api.deps import get_experience_service
from app.main import app
from app.models.experience import ExperienceLog
from app.models.skill import Skill
from app.models.task import TaskSkill
from app.services.exceptions import NotFoundError


class FakeExperienceService:
    def __init__(self):
        self.task_skills: dict[int, list[TaskSkill]] = {}
        self.raise_missing_skill = False
        self.raise_missing_task = False
        self.raise_missing_user = False

    async def get_task_skills(self, task_id: int):
        if self.raise_missing_task:
            raise NotFoundError(f"Task with id {task_id} not found")
        return self.task_skills.get(task_id, [])

    async def set_task_skills(self, task_id: int, data):
        if self.raise_missing_task:
            raise NotFoundError(f"Task with id {task_id} not found")
        if self.raise_missing_skill:
            raise NotFoundError("Skill 404 not found")

        self.task_skills[task_id] = [
            task_skill(
                task_id=task_id,
                skill_id=item.skill_id,
                exp_reward=item.exp_reward,
            )
            for item in data.skills
        ]
        return self.task_skills[task_id]

    async def get_user_log(self, user_id: int, limit: int, offset: int):
        if self.raise_missing_user:
            raise NotFoundError(f"User {user_id} not found")
        logs = [
            ExperienceLog(
                id=2,
                user_id=user_id,
                skill_id=2,
                task_id=20,
                amount=30,
                created_at=datetime(2026, 7, 2, tzinfo=UTC),
            ),
            ExperienceLog(
                id=1,
                user_id=user_id,
                skill_id=1,
                task_id=10,
                amount=50,
                created_at=datetime(2026, 7, 1, tzinfo=UTC),
            ),
        ]
        return logs[offset : offset + limit]


@pytest.fixture
def experience_service_override():
    service = FakeExperienceService()

    async def override_get_experience_service():
        return service

    app.dependency_overrides[get_experience_service] = override_get_experience_service
    yield service
    app.dependency_overrides.pop(get_experience_service, None)


def task_skill(task_id: int, skill_id: int, exp_reward: int) -> TaskSkill:
    item = TaskSkill(
        id=skill_id,
        task_id=task_id,
        skill_id=skill_id,
        exp_reward=exp_reward,
    )
    item.skill = Skill(id=skill_id, name=f"skill-{skill_id}", description="test skill")
    return item


async def test_put_task_skills_sets_rewards(client, experience_service_override):
    resp = await client.put(
        "/api/v1/tasks/1/skills",
        json={"skills": [{"skill_id": 1, "exp_reward": 50}]},
    )

    assert resp.status_code == 200
    assert resp.json() == [
        {
            "skill": {"id": 1, "name": "skill-1", "description": "test skill"},
            "exp_reward": 50,
        }
    ]


async def test_put_task_skills_replaces_rewards(client, experience_service_override):
    await client.put(
        "/api/v1/tasks/1/skills",
        json={"skills": [{"skill_id": 1, "exp_reward": 50}]},
    )
    resp = await client.put(
        "/api/v1/tasks/1/skills",
        json={"skills": [{"skill_id": 2, "exp_reward": 30}]},
    )

    assert resp.status_code == 200
    assert resp.json()[0]["skill"]["id"] == 2
    assert resp.json()[0]["exp_reward"] == 30


async def test_put_task_skills_empty_list_removes_rewards(client, experience_service_override):
    await client.put(
        "/api/v1/tasks/1/skills",
        json={"skills": [{"skill_id": 1, "exp_reward": 50}]},
    )
    resp = await client.put("/api/v1/tasks/1/skills", json={"skills": []})

    assert resp.status_code == 200
    assert resp.json() == []
    assert experience_service_override.task_skills[1] == []


async def test_get_task_skills_returns_rewards(client, experience_service_override):
    experience_service_override.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]

    resp = await client.get("/api/v1/tasks/1/skills")

    assert resp.status_code == 200
    assert resp.json()[0]["exp_reward"] == 50


async def test_put_task_skills_missing_skill_returns_404(client, experience_service_override):
    experience_service_override.raise_missing_skill = True

    resp = await client.put(
        "/api/v1/tasks/1/skills",
        json={"skills": [{"skill_id": 404, "exp_reward": 50}]},
    )

    assert resp.status_code == 404


async def test_put_task_skills_duplicate_skill_id_returns_422(client, experience_service_override):
    resp = await client.put(
        "/api/v1/tasks/1/skills",
        json={
            "skills": [
                {"skill_id": 1, "exp_reward": 50},
                {"skill_id": 1, "exp_reward": 30},
            ]
        },
    )

    assert resp.status_code == 422


async def test_put_task_skills_zero_reward_returns_422(client, experience_service_override):
    resp = await client.put(
        "/api/v1/tasks/1/skills",
        json={"skills": [{"skill_id": 1, "exp_reward": 0}]},
    )

    assert resp.status_code == 422


async def test_get_user_experience_log_returns_history(client, experience_service_override):
    resp = await client.get("/api/v1/users/10/experience-log?limit=1&offset=0")

    assert resp.status_code == 200
    assert resp.json() == [
        {
            "id": 2,
            "user_id": 10,
            "skill_id": 2,
            "task_id": 20,
            "amount": 30,
            "created_at": "2026-07-02T00:00:00Z",
        }
    ]


async def test_get_user_experience_log_missing_user_returns_404(
    client, experience_service_override
):
    experience_service_override.raise_missing_user = True

    resp = await client.get("/api/v1/users/404/experience-log")

    assert resp.status_code == 404
