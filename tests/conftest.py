from dataclasses import dataclass

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import event
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.main import app
from app.models import Base, Label, Task, User
from app.models.enums import MemberStatus
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.services.task_service import TaskService


@pytest_asyncio.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def db_session():
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", poolclass=StaticPool)

    @event.listens_for(engine.sync_engine, "connect")
    def _enable_foreign_keys(dbapi_connection, _connection_record):
        # SQLite silently ignores ON DELETE CASCADE unless this pragma is on.
        dbapi_connection.execute("PRAGMA foreign_keys=ON")

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    session_maker = async_sessionmaker(engine, expire_on_commit=False)
    async with session_maker() as session:
        yield session

    await engine.dispose()


@dataclass(frozen=True)
class TaskFixtures:
    creator: User
    assignee: User
    task: Task
    other_task: Task
    third_task: Task
    backend_label: Label
    frontend_label: Label


def build_task_service(session: AsyncSession) -> TaskService:
    """TaskService over real repositories. XP awarding is not exercised here."""
    return TaskService(
        task_repo=TaskRepository(session),
        user_repo=UserRepository(session),
        experience_awarder=None,
    )


async def seed_task_fixtures(session: AsyncSession) -> TaskFixtures:
    creator = User(
        username="creator",
        email="creator@example.com",
        hashed_password="hashed",
        role="user",
        position="Team Lead",
        member_status=MemberStatus.active.value,
    )
    assignee = User(
        username="developer",
        email="developer@example.com",
        hashed_password="hashed",
        role="user",
        position="Frontend Developer",
        member_status=MemberStatus.active.value,
    )
    session.add_all([creator, assignee])
    await session.flush()

    task = Task(title="Add Kanban", creator_id=creator.id, assignee_id=assignee.id)
    other_task = Task(title="Implement labels", creator_id=creator.id)
    third_task = Task(title="Write docs", creator_id=creator.id)
    backend_label = Label(name="Backend", color="#3B82F6")
    frontend_label = Label(name="Frontend", color="#10B981")
    session.add_all([task, other_task, third_task, backend_label, frontend_label])
    await session.flush()

    # Detach the seeded instances so the code under test reloads them with a real SELECT,
    # the way a request does. Objects constructed in-session keep their relationship
    # collections pinned in the identity map, which would mask stale-read regressions.
    session.expunge_all()

    return TaskFixtures(
        creator=creator,
        assignee=assignee,
        task=task,
        other_task=other_task,
        third_task=third_task,
        backend_label=backend_label,
        frontend_label=frontend_label,
    )
