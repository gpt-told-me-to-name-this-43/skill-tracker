"""E2E-прогон против живого стека (docker compose: api + postgres + redis).

Запуск:
    E2E_BASE_URL=http://localhost:8000 poetry run pytest tests/test_e2e_docker.py

Без E2E_BASE_URL модуль пропускается целиком, обычный прогон pytest не затрагивается.
Каждый прогон создаёт собственных пользователей/навыки с уникальным суффиксом,
поэтому идемпотентен относительно состояния БД.
"""

import os
import uuid

import pytest
import pytest_asyncio
from httpx import AsyncClient

E2E_BASE_URL = os.environ.get("E2E_BASE_URL")

pytestmark = pytest.mark.skipif(
    not E2E_BASE_URL, reason="E2E_BASE_URL не задан: живой стек недоступен"
)


@pytest_asyncio.fixture
async def http():
    async with AsyncClient(base_url=f"{E2E_BASE_URL}/api/v1", timeout=30) as client:
        yield client


async def _register_and_login(http: AsyncClient, tag: str) -> tuple[dict, dict]:
    """Возвращает (данные пользователя, auth-заголовки)."""
    uid = uuid.uuid4().hex[:8]
    payload = {
        "email": f"e2e_{tag}_{uid}@example.com",
        "username": f"e2e_{tag}_{uid}",
        "password": "password-123",
    }
    resp = await http.post("/auth/register", json=payload)
    assert resp.status_code == 201, resp.text
    user = resp.json()
    assert "hashed_password" not in user
    assert user["role"] == "user"

    resp = await http.post(
        "/auth/login", json={"email": payload["email"], "password": payload["password"]}
    )
    assert resp.status_code == 200, resp.text
    token = resp.json()["access_token"]
    return user, {"Authorization": f"Bearer {token}"}


async def test_health():
    async with AsyncClient(base_url=E2E_BASE_URL, timeout=30) as client:
        resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


async def test_auth_contract(http):
    user, headers = await _register_and_login(http, "auth")

    resp = await http.get("/auth/me", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["id"] == user["id"]

    resp = await http.get("/auth/me")
    assert resp.status_code == 401

    resp = await http.get("/auth/me", headers={"Authorization": "Bearer invalid"})
    assert resp.status_code == 401

    resp = await http.post(
        "/auth/login", json={"email": user["email"], "password": "wrong-password"}
    )
    assert resp.status_code == 401


async def test_skill_uniqueness_conflict(http):
    _, headers = await _register_and_login(http, "skill")
    name = f"E2E Skill {uuid.uuid4().hex[:8]}"

    resp = await http.post("/skills", json={"name": name}, headers=headers)
    assert resp.status_code == 201, resp.text

    resp = await http.post("/skills", json={"name": name.upper()}, headers=headers)
    assert resp.status_code == 409


async def test_full_product_loop(http):
    """task -> review -> approval -> done -> XP award -> profile progress."""
    creator, headers = await _register_and_login(http, "creator")
    assignee, _ = await _register_and_login(http, "assignee")

    resp = await http.post(
        "/skills", json={"name": f"E2E Loop {uuid.uuid4().hex[:8]}"}, headers=headers
    )
    assert resp.status_code == 201, resp.text
    skill_id = resp.json()["id"]

    resp = await http.post(
        "/tasks",
        json={"title": "E2E loop task", "description": "e2e", "difficulty": 3},
        headers=headers,
    )
    assert resp.status_code == 201, resp.text
    task = resp.json()
    task_id = task["id"]
    assert task["creator_id"] == creator["id"]
    assert task["status"] == "todo"

    resp = await http.patch(
        f"/tasks/{task_id}/assign", json={"assignee_id": assignee["id"]}, headers=headers
    )
    assert resp.status_code == 200
    assert resp.json()["assignee_id"] == assignee["id"]

    resp = await http.put(
        f"/tasks/{task_id}/skills",
        json={"skills": [{"skill_id": skill_id, "exp_reward": 150}]},
        headers=headers,
    )
    assert resp.status_code == 200, resp.text

    # Гейт workflow: done из todo запрещён
    resp = await http.patch(f"/tasks/{task_id}/status", json={"status": "done"}, headers=headers)
    assert resp.status_code == 400

    for status in ("in_progress", "review"):
        resp = await http.patch(
            f"/tasks/{task_id}/status", json={"status": status}, headers=headers
        )
        assert resp.status_code == 200, resp.text

    # Гейт workflow: done без апрува запрещён
    resp = await http.patch(f"/tasks/{task_id}/status", json={"status": "done"}, headers=headers)
    assert resp.status_code == 400

    resp = await http.patch(f"/tasks/{task_id}/approve", headers=headers)
    assert resp.status_code == 200, resp.text

    resp = await http.patch(f"/tasks/{task_id}/status", json={"status": "done"}, headers=headers)
    assert resp.status_code == 200, resp.text
    assert resp.json()["status"] == "done"

    # У6: UserSkill не существовал — появился с начисленным XP
    resp = await http.get(f"/users/{assignee['id']}/skills", headers=headers)
    assert resp.status_code == 200
    skills = [item for item in resp.json() if item["skill"]["id"] == skill_id]
    assert len(skills) == 1
    assert skills[0]["experience"] == 150
    assert skills[0]["level"] == 2  # 150 XP -> level 2 (значение берём из API)

    resp = await http.get(f"/users/{assignee['id']}/progress", headers=headers)
    assert resp.status_code == 200
    progress = resp.json()
    assert progress["total_experience"] == 150

    resp = await http.get(f"/users/{assignee['id']}/experience-log", headers=headers)
    assert resp.status_code == 200
    log = [item for item in resp.json() if item["task_id"] == task_id]
    assert len(log) == 1
    assert log[0]["amount"] == 150

    # У4 (наблюдаемая часть): повторный done не задваивает XP и лог
    resp = await http.patch(f"/tasks/{task_id}/status", json={"status": "done"}, headers=headers)
    assert resp.status_code == 200

    resp = await http.get(f"/users/{assignee['id']}/experience-log", headers=headers)
    assert len([item for item in resp.json() if item["task_id"] == task_id]) == 1

    # У2: переназначение после done не переносит XP
    resp = await http.patch(
        f"/tasks/{task_id}/assign", json={"assignee_id": creator["id"]}, headers=headers
    )
    assert resp.status_code == 200

    resp = await http.get(f"/users/{creator['id']}/experience-log", headers=headers)
    assert resp.status_code == 200
    assert [item for item in resp.json() if item["task_id"] == task_id] == []

    resp = await http.get(f"/users/{assignee['id']}/skills", headers=headers)
    skills = [item for item in resp.json() if item["skill"]["id"] == skill_id]
    assert skills[0]["experience"] == 150


async def test_empty_profile_returns_zeroes(http):
    user, headers = await _register_and_login(http, "empty")

    resp = await http.get(f"/users/{user['id']}/skills", headers=headers)
    assert resp.status_code == 200
    assert resp.json() == []

    resp = await http.get(f"/users/{user['id']}/progress", headers=headers)
    assert resp.status_code == 200
    progress = resp.json()
    assert progress["total_experience"] == 0
    assert progress["average_level"] == 0
