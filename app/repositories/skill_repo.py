from collections.abc import Sequence

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.skill import Skill
from app.models.user import UserSkill


class SkillRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get(self, skill_id: int) -> Skill | None:
        return await self.session.get(Skill, skill_id)

    async def get_by_name(self, name: str) -> Skill | None:
        result = await self.session.execute(
            select(Skill).where(func.lower(Skill.name) == func.lower(name.strip()))
        )
        return result.scalar_one_or_none()

    async def list(self, limit: int, offset: int) -> Sequence[Skill]:
        result = await self.session.execute(
            select(Skill).order_by(Skill.id).limit(limit).offset(offset)
        )
        return result.scalars().all()

    async def create(self, skill: Skill) -> Skill:
        self.session.add(skill)
        await self.session.flush()
        await self.session.refresh(skill)
        return skill

    async def delete(self, skill: Skill) -> None:
        await self.session.delete(skill)
        await self.session.flush()

    async def get_user_skills(self, user_id: int) -> Sequence[UserSkill]:
        result = await self.session.execute(
            select(UserSkill)
            .where(UserSkill.user_id == user_id)
            .order_by(UserSkill.id)
        )
        return result.scalars().all()

    async def get_user_skill(self, user_id: int, skill_id: int) -> UserSkill | None:
        result = await self.session.execute(
            select(UserSkill).where(
                UserSkill.user_id == user_id,
                UserSkill.skill_id == skill_id
            )
        )
        return result.scalar_one_or_none()

    async def assign_skill_to_user(self, user_id: int, skill_id: int) -> UserSkill:
        user_skill = UserSkill(user_id=user_id, skill_id=skill_id)
        self.session.add(user_skill)
        await self.session.flush()
        await self.session.refresh(user_skill)
        return user_skill