from datetime import datetime

import pytest

from app.models.enums import MemberStatus
from app.models.team import Team, TeamMember
from app.models.user import User
from app.schemas.team import TeamCreate, TeamMembersSet
from app.services.exceptions import BadRequestError, ConflictError
from app.services.team_service import TeamService


def _user(user_id: int, username: str) -> User:
    return User(
        id=user_id,
        username=username,
        email=f"{username}@example.com",
        hashed_password="hashed",
        role="user",
        avatar_url=None,
        position=None,
        member_status=MemberStatus.active.value,
        created_at=datetime(2026, 7, 10, 10, 0, 0),
        updated_at=datetime(2026, 7, 10, 10, 0, 0),
    )


def _team(team_id: int, name: str, lead_id: int | None = None) -> Team:
    return Team(
        id=team_id,
        name=name,
        description=None,
        lead_id=lead_id,
        created_at=datetime(2026, 7, 10, 10, 0, 0),
        updated_at=datetime(2026, 7, 10, 10, 0, 0),
        memberships=[],
    )


class FakeTeamRepo:
    def __init__(self) -> None:
        self.users = {
            1: _user(1, "ivan"),
            2: _user(2, "olga"),
        }
        self.teams = {
            1: _team(1, "Backend Team", lead_id=1),
            2: _team(2, "QA Team"),
        }
        self.memberships: list[TeamMember] = []
        self.next_team_id = 3
        self.next_membership_id = 1
        self._add_existing_membership(1, 1)

    def _add_existing_membership(self, team_id: int, user_id: int) -> None:
        membership = TeamMember(
            id=self.next_membership_id,
            team_id=team_id,
            user_id=user_id,
            created_at=datetime(2026, 7, 10, 10, 0, 0),
        )
        self.next_membership_id += 1
        self.memberships.append(membership)
        self._sync_relationships()

    def _sync_relationships(self) -> None:
        for team in self.teams.values():
            team.memberships = []
            team.lead = self.users.get(team.lead_id) if team.lead_id is not None else None
        for user in self.users.values():
            user.team_membership = None

        for membership in self.memberships:
            membership.team = self.teams[membership.team_id]
            membership.user = self.users[membership.user_id]
            self.users[membership.user_id].team_membership = membership

    async def list_teams(self) -> list[Team]:
        self._sync_relationships()
        return list(self.teams.values())

    async def get_team_by_id(self, team_id: int) -> Team | None:
        self._sync_relationships()
        return self.teams.get(team_id)

    async def get_team_by_name(self, name: str) -> Team | None:
        return next(
            (team for team in self.teams.values() if team.name.lower() == name.lower()),
            None,
        )

    async def create_team(self, name: str, description: str | None) -> Team:
        team = _team(self.next_team_id, name)
        team.description = description
        self.teams[team.id] = team
        self.next_team_id += 1
        return team

    async def update_team(self, team: Team, fields: dict) -> Team:
        for key, value in fields.items():
            setattr(team, key, value)
        self._sync_relationships()
        return team

    async def get_users_by_ids(self, user_ids: list[int]) -> list[User]:
        return [self.users[user_id] for user_id in user_ids if user_id in self.users]

    async def get_memberships_for_users(self, user_ids: list[int]) -> list[TeamMember]:
        self._sync_relationships()
        return [membership for membership in self.memberships if membership.user_id in user_ids]

    async def get_memberships_for_team(self, team_id: int) -> list[TeamMember]:
        self._sync_relationships()
        return [membership for membership in self.memberships if membership.team_id == team_id]

    async def delete_membership(self, membership: TeamMember) -> None:
        self.memberships = [item for item in self.memberships if item.id != membership.id]
        self._sync_relationships()

    async def add_membership(self, team_id: int, user_id: int) -> TeamMember:
        membership = TeamMember(
            id=self.next_membership_id,
            team_id=team_id,
            user_id=user_id,
            created_at=datetime(2026, 7, 10, 10, 0, 0),
        )
        self.next_membership_id += 1
        self.memberships.append(membership)
        self._sync_relationships()
        return membership

    async def set_team_lead(self, team: Team, lead_id: int | None) -> None:
        team.lead_id = lead_id
        self._sync_relationships()


async def test_create_team_rejects_duplicate_name_case_insensitive():
    service = TeamService(FakeTeamRepo())

    with pytest.raises(ConflictError):
        await service.create_team(TeamCreate(name=" backend team "))


async def test_set_members_rejects_lead_outside_team():
    service = TeamService(FakeTeamRepo())

    with pytest.raises(BadRequestError):
        await service.set_members(2, TeamMembersSet(user_ids=[2], lead_id=1))


async def test_set_members_moves_user_from_previous_team_and_clears_old_lead():
    repo = FakeTeamRepo()
    service = TeamService(repo)

    qa_team = await service.set_members(2, TeamMembersSet(user_ids=[1, 2], lead_id=2))

    backend_team = await repo.get_team_by_id(1)
    assert backend_team is not None
    assert backend_team.lead_id is None
    assert backend_team.member_count == 0
    assert qa_team.lead_id == 2
    assert sorted(member.id for member in qa_team.members) == [1, 2]
