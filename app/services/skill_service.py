from collections.abc import Sequence

from app.models.skill import Skill
from app.repositories.skill_repo import SkillRepository
from app.schemas.skill import SkillCreate, SkillUpdate
from app.services.exceptions import ConflictError, NotFoundError


class SkillService:
    def __init__(self, repo: SkillRepository) -> None:
        self.repo = repo

    async def get(self, skill_id: int) -> Skill:
        skill = await self.repo.get(skill_id)
        if skill is None:
            raise NotFoundError(f"Skill {skill_id} not found")
        return skill

    async def list(self, limit: int, offset: int) -> Sequence[Skill]:
        return await self.repo.list(limit, offset)

    async def create(self, data: SkillCreate) -> Skill:
        if await self.repo.get_by_name(data.name) is not None:
            raise ConflictError(f"Skill '{data.name}' already exists")
        skill = Skill(name=data.name, description=data.description)
        return await self.repo.create(skill)

    async def update(self, skill_id: int, data: SkillUpdate) -> Skill:
        skill = await self.get(skill_id)  # бросит NotFoundError
        if data.name is not None:
            skill.name = data.name
        if data.description is not None:
            skill.description = data.description
        await self.repo.session.flush()
        return skill

    async def delete(self, skill_id: int) -> None:
        skill = await self.get(skill_id)
        await self.repo.delete(skill)
