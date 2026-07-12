import pytest_asyncio
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.core.database import get_db
from app.main import app
from app.models import Base


@pytest_asyncio.fixture(autouse=True)
async def db_override():
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", poolclass=StaticPool)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_maker = async_sessionmaker(engine, expire_on_commit=False)

    async def override_get_db():
        async with session_maker() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[get_db] = override_get_db
    yield
    app.dependency_overrides.pop(get_db, None)
    await engine.dispose()


REGISTER_PAYLOAD = {
    "email": "dev@example.com",
    "username": "developer",
    "password": "secret-123",
}


async def test_register_login_me_flow(client):
    resp = await client.post("/api/v1/auth/register", json=REGISTER_PAYLOAD)
    assert resp.status_code == 201
    assert "hashed_password" not in resp.json()

    resp = await client.post(
        "/api/v1/auth/login",
        json={"email": REGISTER_PAYLOAD["email"], "password": REGISTER_PAYLOAD["password"]},
    )
    assert resp.status_code == 200
    token = resp.json()["access_token"]

    resp = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["email"] == REGISTER_PAYLOAD["email"]


async def test_me_without_token_returns_401(client):
    resp = await client.get("/api/v1/auth/me")
    assert resp.status_code == 401


async def test_register_password_over_72_bytes_returns_422(client):
    # bcrypt учитывает только первые 72 байта: более длинные пароли должны отклоняться,
    # иначе разные пароли с одинаковым 72-байтовым префиксом становятся эквивалентными.
    payload = {**REGISTER_PAYLOAD, "password": "A" * 72 + "BBBBBBBB"}
    resp = await client.post("/api/v1/auth/register", json=payload)
    assert resp.status_code == 422


async def test_register_password_of_72_bytes_is_accepted(client):
    payload = {**REGISTER_PAYLOAD, "password": "A" * 72}
    resp = await client.post("/api/v1/auth/register", json=payload)
    assert resp.status_code == 201


async def test_login_non_json_body_returns_422(client):
    resp = await client.post(
        "/api/v1/auth/login",
        content=b"not-json",
        headers={"Content-Type": "text/plain"},
    )
    assert resp.status_code == 422
    assert resp.json()["error"]["message"] == "Validation error"


async def test_openapi_declares_http_bearer_auth(client):
    resp = await client.get("/openapi.json")
    assert resp.status_code == 200

    schemes = resp.json()["components"]["securitySchemes"]
    assert any(
        scheme.get("type") == "http" and scheme.get("scheme") == "bearer"
        for scheme in schemes.values()
    )
    assert all(scheme.get("type") != "oauth2" for scheme in schemes.values())
