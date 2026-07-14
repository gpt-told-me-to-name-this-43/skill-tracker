"""Интеграционные тесты POST /api/v1/integrations/github/sync."""

from app.api.deps import get_current_user, get_github_import_service
from app.main import app
from app.models.enums import MemberStatus
from app.models.user import User
from app.services.exceptions import NotFoundError


def _current_user() -> User:
    user = User(
        username="importer",
        email="importer@example.com",
        hashed_password="hashed",
        role="user",
        member_status=MemberStatus.active.value,
    )
    user.id = 99
    return user


async def _override_current_user() -> User:
    return _current_user()


class FakeImportService:
    def __init__(self, result=None, error: Exception | None = None):
        self.result = result
        self.error = error
        self.calls: list[int] = []

    async def sync(self, current_user_id: int) -> dict:
        self.calls.append(current_user_id)
        if self.error is not None:
            raise self.error
        return self.result


async def test_sync_returns_counters(client):
    service = FakeImportService(result={"created": 3, "updated": 1, "users_created": 2})

    async def override_service() -> FakeImportService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_github_import_service] = override_service

    resp = await client.post("/api/v1/integrations/github/sync")

    assert resp.status_code == 200
    assert resp.json() == {"created": 3, "updated": 1, "users_created": 2}
    assert service.calls == [99]


async def test_sync_requires_authentication(client):
    resp = await client.post("/api/v1/integrations/github/sync")

    assert resp.status_code == 401


async def test_sync_missing_repo_maps_to_404(client):
    service = FakeImportService(error=NotFoundError("GitHub repository acme/missing not found"))

    async def override_service() -> FakeImportService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_github_import_service] = override_service

    resp = await client.post("/api/v1/integrations/github/sync")

    assert resp.status_code == 404
