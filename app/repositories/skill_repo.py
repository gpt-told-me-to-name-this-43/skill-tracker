from collections.abc import Sequence

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.skill import Skill


class SkillRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get(self, skill_id: int) -> Skill | None:
        return await self.session.get(Skill, skill_id)

    async def get_by_name(self, name: str) -> Skill | None:
        result = await self.session.execute(select(Skill).where(Skill.name == name))
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
