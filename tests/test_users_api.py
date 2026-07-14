import pytest

@pytest.mark.asyncio
async def test_users_api(async_client, test_user):
    # Список пользователей с пагинацией
    list_resp = await async_client.get("/api/v1/users?limit=5&offset=0")
    assert list_resp.status_code == 200
    assert isinstance(list_resp.json(), list)

    # Получение по ID
    user_id = test_user["id"]
    id_resp = await async_client.get(f"/api/v1/users/{user_id}")
    assert id_resp.status_code == 200
    assert "hashed_password" not in id_resp.json()

    # Несуществующий юзер
    resp_404 = await async_client.get("/api/v1/users/999999")
    assert resp_404.status_code == 404