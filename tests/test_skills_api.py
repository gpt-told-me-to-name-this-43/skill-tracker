import pytest

from app.api.deps import get_skill_service
from app.main import app
from app.models.skill import Skill
from app.services.skill_service import SkillService


class FakeSession:
    async def flush(self):
        pass


class FakeSkillRepo:
    def __init__(self):
        self._items: dict[int, Skill] = {}
        self._counter = 0
        self.session = FakeSession()

    async def get(self, skill_id):
        return self._items.get(skill_id)

    async def get_by_name(self, name):
        # Мимикрирует регистронезависимый поиск реального репозитория.
        target = name.strip().lower()
        return next((s for s in self._items.values() if s.name.lower() == target), None)

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


async def test_update_explicit_null_clears_description(client):
    resp = await client.post("/api/v1/skills", json={"name": "Go", "description": "Backend"})
    skill_id = resp.json()["id"]

    resp = await client.patch(f"/api/v1/skills/{skill_id}", json={"description": None})
    assert resp.status_code == 200
    assert resp.json()["description"] is None

    resp = await client.get(f"/api/v1/skills/{skill_id}")
    assert resp.json()["description"] is None


async def test_update_without_description_keeps_it(client):
    resp = await client.post("/api/v1/skills", json={"name": "Go", "description": "Backend"})
    skill_id = resp.json()["id"]

    resp = await client.patch(f"/api/v1/skills/{skill_id}", json={"name": "Golang"})
    assert resp.status_code == 200
    assert resp.json()["description"] == "Backend"


async def test_update_whitespace_name_returns_409(client):
    resp = await client.post("/api/v1/skills", json={"name": "Rust"})
    skill_id = resp.json()["id"]

    resp = await client.patch(f"/api/v1/skills/{skill_id}", json={"name": "   "})
    assert resp.status_code == 409

    resp = await client.get(f"/api/v1/skills/{skill_id}")
    assert resp.json()["name"] == "Rust"


async def test_update_duplicate_name_returns_409(client):
    await client.post("/api/v1/skills", json={"name": "Python"})
    resp = await client.post("/api/v1/skills", json={"name": "Docker"})
    docker_id = resp.json()["id"]

    resp = await client.patch(f"/api/v1/skills/{docker_id}", json={"name": "python"})
    assert resp.status_code == 409

    resp = await client.get(f"/api/v1/skills/{docker_id}")
    assert resp.json()["name"] == "Docker"


async def test_update_own_name_case_change_returns_200(client):
    resp = await client.post("/api/v1/skills", json={"name": "docker"})
    skill_id = resp.json()["id"]

    resp = await client.patch(f"/api/v1/skills/{skill_id}", json={"name": "Docker"})
    assert resp.status_code == 200
    assert resp.json()["name"] == "Docker"
