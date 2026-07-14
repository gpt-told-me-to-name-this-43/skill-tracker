import pytest

@pytest.mark.asyncio
async def test_tasks_create_and_filters(async_client, test_user, auth_headers):
    # Создание
    payload = {"title": "Task", "description": "Desc", "difficulty": 3, "deadline": "2099-12-31T23:59:59"}
    resp = await async_client.post("/api/v1/tasks", json=payload, headers=auth_headers)
    assert resp.status_code == 201
    
    task = resp.json()
    assert task["creator_id"] == test_user["id"]
    assert task["status"] == "todo"

    # Дедлайн в прошлом (400)
    bad_resp = await async_client.post("/api/v1/tasks", json={"title": "T", "deadline": "2000-01-01T00:00:00"}, headers=auth_headers)
    assert bad_resp.status_code == 400

    # Difficulty < 1 (422)
    diff_resp = await async_client.post("/api/v1/tasks", json={"title": "T", "difficulty": 0}, headers=auth_headers)
    assert diff_resp.status_code == 422

    # Фильтры списка
    list_resp = await async_client.get(f"/api/v1/tasks?status=todo&assignee_id={test_user['id']}&difficulty=3", headers=auth_headers)
    assert list_resp.status_code == 200

@pytest.mark.asyncio
async def test_tasks_update_and_assign(async_client, test_user, auth_headers):
    resp = await async_client.post("/api/v1/tasks", json={"title": "Original", "difficulty": 1}, headers=auth_headers)
    task_id = resp.json()["id"]

    # PATCH (status и assignee_id игнорируются)
    patch_resp = await async_client.patch(f"/api/v1/tasks/{task_id}", json={"title": "New", "status": "done", "assignee_id": 999}, headers=auth_headers)
    assert patch_resp.status_code == 200
    assert patch_resp.json()["title"] == "New"
    assert patch_resp.json()["status"] != "done"

    # Assign
    assign_resp = await async_client.patch(f"/api/v1/tasks/{task_id}/assign", json={"assignee_id": test_user["id"]}, headers=auth_headers)
    assert assign_resp.status_code == 200
    
    assign_null = await async_client.patch(f"/api/v1/tasks/{task_id}/assign", json={"assignee_id": None}, headers=auth_headers)
    assert assign_null.status_code == 200

@pytest.mark.asyncio
async def test_tasks_status_endpoint(async_client, auth_headers):
    resp = await async_client.post("/api/v1/tasks", json={"title": "Status test"}, headers=auth_headers)
    task_id = resp.json()["id"]

    # Проверка переходов статусов (state machine нет, значит можно любой)
    for status in ["in_progress", "review", "done", "todo"]:
        s_resp = await async_client.patch(f"/api/v1/tasks/{task_id}/status", json={"status": status}, headers=auth_headers)
        assert s_resp.status_code == 200
        assert s_resp.json()["status"] == status

    # Невалидный статус
    bad_stat = await async_client.patch(f"/api/v1/tasks/{task_id}/status", json={"status": "invalid"}, headers=auth_headers)
    assert bad_stat.status_code == 422