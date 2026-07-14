import pytest

@pytest.mark.asyncio
async def test_auth_register_success(async_client):
    payload = {"email": " Dev@Example.COM ", "username": "dev_user", "password": "password123"}
    resp = await async_client.post("/api/v1/auth/register", json=payload)
    
    assert resp.status_code == 201
    data = resp.json()
    assert data["email"] == "dev@example.com"  # Lowercase and trim check
    assert data["role"] == "user"
    assert "password" not in data
    assert "hashed_password" not in data

@pytest.mark.asyncio
async def test_auth_register_conflicts_and_validation(async_client):
    valid_payload = {"email": "test@example.com", "username": "validuser", "password": "password123"}
    await async_client.post("/api/v1/auth/register", json=valid_payload)
    
    # Дубль email (в другом регистре)
    resp_email = await async_client.post("/api/v1/auth/register", json={"email": "TEST@example.com", "username": "newuser", "password": "pwd"})
    assert resp_email.status_code == 409
    
    # Дубль username
    resp_uname = await async_client.post("/api/v1/auth/register", json={"email": "new@example.com", "username": "validuser", "password": "pwd"})
    assert resp_uname.status_code == 409

    # Невалидные данные (422)
    resp_bad = await async_client.post("/api/v1/auth/register", json={"email": "not-an-email", "username": "a", "password": "1"})
    assert resp_bad.status_code == 422

@pytest.mark.asyncio
async def test_auth_login_and_me(async_client):
    await async_client.post("/api/v1/auth/register", json={"email": "log@x.com", "username": "log_user", "password": "password123"})
    
    # Успешный логин
    login_resp = await async_client.post("/api/v1/auth/login", json={"email": "log@x.com", "password": "password123"})
    assert login_resp.status_code == 200
    assert login_resp.json()["token_type"] == "bearer"
    token = login_resp.json()["access_token"]
    
    # GET /me
    me_resp = await async_client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_resp.status_code == 200
    assert "hashed_password" not in me_resp.json()

    # Ошибки 401
    bad_login = await async_client.post("/api/v1/auth/login", json={"email": "log@x.com", "password": "wrong"})
    assert bad_login.status_code == 401
    
    bad_token = await async_client.get("/api/v1/auth/me", headers={"Authorization": "Bearer invalid"})
    assert bad_token.status_code == 401
    
    no_token = await async_client.get("/api/v1/auth/me")
    assert no_token.status_code == 401