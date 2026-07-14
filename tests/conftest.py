import uuid
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.main import app
# Пробуем импортировать базу (если она лежит в app.core.database или app.database)
try:
    from app.core.database import engine, get_db
except ImportError:
    from app.database import engine, get_db  # Fallback

@pytest_asyncio.fixture(scope="function")
async def db_session():
    """Отдельная тестовая БД / rollback после тестов для изоляции (требование таски)"""
    connection = await engine.connect()
    transaction = await connection.begin()
    
    session_maker = async_sessionmaker(bind=connection, class_=AsyncSession, expire_on_commit=False)
    session = session_maker()
    
    yield session
    
    await session.close()
    await transaction.rollback()
    await connection.close()

@pytest_asyncio.fixture(autouse=True)
def override_dependency(db_session):
    """Очистка dependency_overrides после тестов"""
    app.dependency_overrides[get_db] = lambda: db_session
    yield
    app.dependency_overrides.clear()

@pytest_asyncio.fixture(scope="function")
async def async_client():
    """Async client для FastAPI"""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client

@pytest_asyncio.fixture(scope="function")
async def auth_headers(async_client):
    """Фикстура для заголовков авторизации"""
    uid = uuid.uuid4().hex[:6]
    user_data = {"email": f"test_{uid}@example.com", "username": f"user_{uid}", "password": "password123"}
    await async_client.post("/api/v1/auth/register", json=user_data)
    resp = await async_client.post("/api/v1/auth/login", json={"email": user_data["email"], "password": "password123"})
    token = resp.json().get("access_token", "fake_token")
    return {"Authorization": f"Bearer {token}"}

@pytest_asyncio.fixture(scope="function")
async def test_user(async_client, auth_headers):
    """Фикстура для получения данных текущего пользователя"""
    resp = await async_client.get("/api/v1/auth/me", headers=auth_headers)
    return resp.json()