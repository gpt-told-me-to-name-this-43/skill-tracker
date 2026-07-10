from collections.abc import Sequence

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.user import Team, TeamMember, User


class UserRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get_user_by_id(self, user_id: int) -> User | None:
        return await self.session.get(User, user_id)

    async def get(self, user_id: int) -> User | None:
        return await self.get_user_by_id(user_id)

    async def get_user_by_email(self, email: str) -> User | None:
        result = await self.session.execute(select(User).where(User.email == email))
        return result.scalar_one_or_none()

    async def get_user_by_username(self, username: str) -> User | None:
        result = await self.session.execute(select(User).where(User.username == username))
        return result.scalar_one_or_none()

    async def list_users(
        self,
        limit: int,
        offset: int,
        team_id: int | None = None,
        member_status: str | None = None,
    ) -> list[User]:
        query = select(User).options(
            selectinload(User.team_membership).selectinload(TeamMember.team)
        )
        if team_id is not None:
            query = query.join(TeamMember).where(TeamMember.team_id == team_id)
        if member_status is not None:
            query = query.where(User.member_status == member_status)

        result = await self.session.execute(query.limit(limit).offset(offset).order_by(User.id))
        return list(result.scalars().all())

    async def create_user(
        self, email: str, username: str, hashed_password: str, role: str = "user"
    ) -> User:
        user = User(
            email=email,
            username=username,
            hashed_password=hashed_password,
            role=role,
        )
        self.session.add(user)
        await self.session.flush()
        await self.session.refresh(user)
        return user

    async def update_workspace_profile(self, user: User, fields: dict) -> User:
        for key, value in fields.items():
            setattr(user, key, value)
        await self.session.flush()
        await self.session.refresh(user)
        return user

    async def list_teams(self) -> list[Team]:
        result = await self.session.execute(
            select(Team)
            .options(
                selectinload(Team.lead),
                selectinload(Team.members).selectinload(TeamMember.user),
            )
            .order_by(Team.name)
        )
        return list(result.scalars().all())

    async def get_team_by_id(self, team_id: int) -> Team | None:
        result = await self.session.execute(
            select(Team)
            .options(
                selectinload(Team.lead),
                selectinload(Team.members).selectinload(TeamMember.user),
            )
            .where(Team.id == team_id)
        )
        return result.scalar_one_or_none()

    async def get_team_by_name_ci(self, name: str) -> Team | None:
        result = await self.session.execute(select(Team).where(Team.name.ilike(name)))
        return result.scalar_one_or_none()

    async def create_team(self, name: str, description: str | None) -> Team:
        team = Team(name=name, description=description)
        self.session.add(team)
        await self.session.flush()
        await self.session.refresh(team)
        return team

    async def update_team(self, team: Team, fields: dict) -> Team:
        for key, value in fields.items():
            setattr(team, key, value)
        await self.session.flush()
        await self.session.refresh(team)
        return team

    async def list_memberships_by_user_ids(self, user_ids: Sequence[int]) -> list[TeamMember]:
        if not user_ids:
            return []
        result = await self.session.execute(
            select(TeamMember)
            .options(selectinload(TeamMember.team))
            .where(TeamMember.user_id.in_(user_ids))
        )
        return list(result.scalars().all())

    async def replace_team_members(
        self,
        team: Team,
        user_ids: Sequence[int],
        lead_id: int | None,
    ) -> Team:
        memberships = await self.list_memberships_by_user_ids(user_ids)
        moved_from_team_ids = {
            membership.team_id
            for membership in memberships
            if membership.team_id != team.id
        }

        await self.session.execute(delete(TeamMember).where(TeamMember.user_id.in_(user_ids)))
        await self.session.execute(delete(TeamMember).where(TeamMember.team_id == team.id))

        for user_id in user_ids:
            self.session.add(TeamMember(team_id=team.id, user_id=user_id))

        if moved_from_team_ids:
            result = await self.session.execute(
                select(Team).where(Team.id.in_(moved_from_team_ids))
            )
            for old_team in result.scalars().all():
                if old_team.lead_id in user_ids:
                    old_team.lead_id = None

        team.lead_id = lead_id
        await self.session.flush()
        return await self.get_team_by_id(team.id)
