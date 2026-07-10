from datetime import datetime

from app.api.deps import get_current_user, get_user_service
from app.main import app
from app.models.enums import MemberStatus
from app.models.team import Team, TeamMember
from app.models.user import User


def _user(
    user_id: int,
    username: str,
    email: str,
    member_status: str = MemberStatus.active.value,
) -> User:
    return User(
        id=user_id,
        username=username,
        email=email,
        hashed_password="hashed",
        role="user",
        avatar_url=None,
        position=None,
        member_status=member_status,
        created_at=datetime(2026, 7, 10, 10, 0, 0),
        updated_at=datetime(2026, 7, 10, 10, 0, 0),
    )


def _team(team_id: int, name: str) -> Team:
    return Team(
        id=team_id,
        name=name,
        description=None,
        lead_id=None,
        created_at=datetime(2026, 7, 10, 10, 0, 0),
        updated_at=datetime(2026, 7, 10, 10, 0, 0),
    )


class FakeUserService:
    def __init__(self) -> None:
        self.users = [
            _user(1, "active-user", "active@example.com"),
            _user(2, "away-user", "away@example.com", MemberStatus.away.value),
        ]
        team = _team(1, "QA Team")
        membership = TeamMember(
            id=1,
            team_id=team.id,
            user_id=2,
            team=team,
            user=self.users[1],
            created_at=datetime(2026, 7, 10, 10, 0, 0),
        )
        self.users[1].team_membership = membership

    async def list_users(
        self,
        limit: int,
        offset: int,
        team_id: int | None = None,
        member_status: MemberStatus | None = None,
    ) -> list[User]:
        users = self.users
        if team_id is not None:
            users = [
                user
                for user in users
                if user.team_membership is not None and user.team_membership.team_id == team_id
            ]
        if member_status is not None:
            users = [user for user in users if user.member_status == member_status.value]
        return users[offset : offset + limit]

    async def get_user_by_id(self, user_id: int) -> User:
        return next(user for user in self.users if user.id == user_id)

    async def update_workspace_profile(self, user_id: int, data) -> User:
        user = await self.get_user_by_id(user_id)
        fields = data.model_dump(exclude_unset=True)
        for key, value in fields.items():
            setattr(user, key, value.value if isinstance(value, MemberStatus) else value)
        return user


async def _override_current_user() -> User:
    return _user(99, "current-user", "current@example.com")


async def test_auth_me_keeps_email(client):
    app.dependency_overrides[get_current_user] = _override_current_user

    resp = await client.get("/api/v1/auth/me")

    assert resp.status_code == 200
    assert resp.json()["email"] == "current@example.com"
    assert resp.json()["member_status"] == "active"


async def test_list_users_omits_email_and_filters_by_member_status(client):
    service = FakeUserService()

    async def override_user_service() -> FakeUserService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_user_service] = override_user_service

    resp = await client.get("/api/v1/users?member_status=away")

    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 1
    assert body[0]["username"] == "away-user"
    assert body[0]["member_status"] == "away"
    assert body[0]["team"] == {"id": 1, "name": "QA Team"}
    assert "email" not in body[0]


async def test_list_users_filters_by_team_id(client):
    service = FakeUserService()

    async def override_user_service() -> FakeUserService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_user_service] = override_user_service

    resp = await client.get("/api/v1/users?team_id=1")

    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 1
    assert body[0]["username"] == "away-user"
    assert body[0]["team"] == {"id": 1, "name": "QA Team"}

    resp = await client.get("/api/v1/users?team_id=99")

    assert resp.status_code == 200
    assert resp.json() == []


async def test_update_workspace_profile_updates_display_fields(client):
    service = FakeUserService()

    async def override_user_service() -> FakeUserService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_user_service] = override_user_service

    resp = await client.patch(
        "/api/v1/users/1/workspace-profile",
        json={
            "avatar_url": "  https://example.com/avatar.png  ",
            "position": "  QA Engineer  ",
            "member_status": "away",
        },
    )

    assert resp.status_code == 200
    body = resp.json()
    assert body["avatar_url"] == "https://example.com/avatar.png"
    assert body["position"] == "QA Engineer"
    assert body["member_status"] == "away"
    assert "email" not in body


async def test_update_workspace_profile_rejects_null_member_status(client):
    service = FakeUserService()

    async def override_user_service() -> FakeUserService:
        return service

    app.dependency_overrides[get_current_user] = _override_current_user
    app.dependency_overrides[get_user_service] = override_user_service

    resp = await client.patch(
        "/api/v1/users/1/workspace-profile",
        json={"member_status": None},
    )

    assert resp.status_code == 422
