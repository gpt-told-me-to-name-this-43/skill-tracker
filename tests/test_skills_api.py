import pytest

@pytest.mark.asyncio
async def test_skills_crud(async_client, auth_headers):
    # Создание и trim имени
    resp = await async_client.post("/api/v1/skills", json={"name": " Backend "}, headers=auth_headers)
    assert resp.status_code == 201
    assert resp.json()["name"] == "Backend"

    # Дубль в другом регистре (409)
    resp_dup = await async_client.post("/api/v1/skills", json={"name": "BACKEND"}, headers=auth_headers)
    assert resp_dup.status_code == 409

    # Список
    list_resp = await async_client.get("/api/v1/skills", headers=auth_headers)
    assert list_resp.status_code == 200
    assert isinstance(list_resp.json(), list)

@pytest.mark.asyncio
async def test_user_skills_and_progress(async_client, test_user, auth_headers):
    user_id = test_user["id"]
    
    # Пользователь без навыков (200 и нули)
    prog_resp = await async_client.get(f"/api/v1/users/{user_id}/progress", headers=auth_headers)
    assert prog_resp.status_code == 200
    assert prog_resp.json()["total_experience"] == 0
    assert prog_resp.json()["skills"] == []

    # Создаем навык для теста
    skill = await async_client.post("/api/v1/skills", json={"name": "TestSkill"}, headers=auth_headers)
    skill_id = skill.json()["id"]

    # Назначаем навык
    assign = await async_client.post(f"/api/v1/users/{user_id}/skills", json={"skill_id": skill_id}, headers=auth_headers)
    assert assign.status_code in [200, 201]

    # Повторное назначение (409)
    dup_assign = await async_client.post(f"/api/v1/users/{user_id}/skills", json={"skill_id": skill_id}, headers=auth_headers)
    assert dup_assign.status_code == 409

    # Несуществующий навык и юзер (404)
    assert (await async_client.post(f"/api/v1/users/999/skills", json={"skill_id": skill_id}, headers=auth_headers)).status_code == 404
    assert (await async_client.post(f"/api/v1/users/{user_id}/skills", json={"skill_id": 999}, headers=auth_headers)).status_code == 404