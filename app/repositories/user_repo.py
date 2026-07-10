from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.team import TeamMember
from app.models.user import User


class UserRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get_user_by_id(self, user_id: int) -> User | None:
        result = await self.session.execute(
            select(User)
            .where(User.id == user_id)
            .options(selectinload(User.team_membership).selectinload(TeamMember.team))
        )
        return result.scalar_one_or_none()

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
            query = query.join(TeamMember, TeamMember.user_id == User.id).where(
                TeamMember.team_id == team_id
            )
        if member_status is not None:
            query = query.where(User.member_status == member_status)

        result = await self.session.execute(query.limit(limit).offset(offset).order_by(User.id))
        return list(result.scalars().all())

    async def update_workspace_profile(self, user: User, fields: dict) -> User:
        for key, value in fields.items():
            setattr(user, key, value)

        await self.session.flush()
        await self.session.refresh(user)
        return user

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
