import pytest

from app.api.deps import get_skill_service
from app.main import app
from app.models.skill import Skill
from app.services.skill_service import SkillService


class FakeSkillRepo:
    def __init__(self):
        self._items: dict[int, Skill] = {}
        self._counter = 0

    async def get(self, skill_id):
        return self._items.get(skill_id)

    async def get_by_name(self, name):
        return next((s for s in self._items.values() if s.name == name), None)

    async def create(self, skill):
        self._counter += 1
        skill.id = self._counter
        self._items[skill.id] = skill
        return skill


@pytest.fixture(autouse=True)
def skill_service_override():
    service = SkillService(FakeSkillRepo())

    async def override_get_skill_service():
        return service

    app.dependency_overrides[get_skill_service] = override_get_skill_service
    yield
    app.dependency_overrides.pop(get_skill_service, None)


async def test_create_and_get(client):
    resp = await client.post("/api/v1/skills", json={"name": "Python", "description": "Backend"})
    assert resp.status_code == 201
    created = resp.json()
    assert created["name"] == "Python"

    resp = await client.get(f"/api/v1/skills/{created['id']}")
    assert resp.status_code == 200
    assert resp.json()["name"] == "Python"


async def test_get_missing_returns_404(client):
    resp = await client.get("/api/v1/skills/999999")
    assert resp.status_code == 404


async def test_create_duplicate_returns_409(client):
    await client.post("/api/v1/skills", json={"name": "Docker"})
    resp = await client.post("/api/v1/skills", json={"name": "Docker"})
    assert resp.status_code == 409
