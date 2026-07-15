from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.team import Team, TeamMember
from app.models.user import User


class TeamRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    def _team_options(self):
        return (
            selectinload(Team.lead),
            selectinload(Team.memberships).selectinload(TeamMember.user),
        )

    async def list_teams(self) -> list[Team]:
        result = await self.session.execute(
            select(Team).options(*self._team_options()).order_by(Team.id)
        )
        return list(result.scalars().all())

    async def get_team_by_id(self, team_id: int) -> Team | None:
        result = await self.session.execute(
            select(Team).where(Team.id == team_id).options(*self._team_options())
        )
        return result.scalar_one_or_none()

    async def get_team_by_name(self, name: str) -> Team | None:
        result = await self.session.execute(
            select(Team).where(func.lower(Team.name) == name.lower())
        )
        return result.scalar_one_or_none()

    async def create_team(self, name: str, description: str | None) -> Team:
        team = Team(name=name, description=description)
        self.session.add(team)
        await self.session.flush()
        return await self.get_team_by_id(team.id)

    async def update_team(self, team: Team, fields: dict) -> Team:
        for key, value in fields.items():
            setattr(team, key, value)

        await self.session.flush()
        return await self.get_team_by_id(team.id)

    async def get_users_by_ids(self, user_ids: list[int]) -> list[User]:
        if not user_ids:
            return []

        result = await self.session.execute(select(User).where(User.id.in_(user_ids)))
        return list(result.scalars().all())

    async def get_memberships_for_users(self, user_ids: list[int]) -> list[TeamMember]:
        if not user_ids:
            return []

        result = await self.session.execute(
            select(TeamMember)
            .where(TeamMember.user_id.in_(user_ids))
            .options(selectinload(TeamMember.team))
        )
        return list(result.scalars().all())

    async def get_memberships_for_team(self, team_id: int) -> list[TeamMember]:
        result = await self.session.execute(
            select(TeamMember)
            .where(TeamMember.team_id == team_id)
            .options(selectinload(TeamMember.team))
        )
        return list(result.scalars().all())

    async def delete_membership(self, membership: TeamMember) -> None:
        await self.session.delete(membership)
        await self.session.flush()

    async def add_membership(self, team_id: int, user_id: int) -> TeamMember:
        membership = TeamMember(team_id=team_id, user_id=user_id)
        self.session.add(membership)
        await self.session.flush()
        return membership

    async def set_team_lead(self, team: Team, lead_id: int | None) -> None:
        team.lead_id = lead_id
        await self.session.flush()
