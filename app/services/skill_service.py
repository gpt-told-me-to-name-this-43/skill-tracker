from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from app.models.skill import Skill
from app.repositories.skill_repo import SkillRepository
from app.schemas.skill import (
    SkillCreate,
    SkillRead,
    SkillUpdate,
    UserProgressRead,
    UserSkillRead,
)
from app.services.exceptions import ConflictError, NotFoundError


@dataclass
class SkillProgress:
    level: int
    current_level_xp: int
    next_level_xp: int
    progress_to_next_level: int


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
        name = data.name.strip()
        if not name:
            raise ConflictError("Skill name cannot be empty")

        if await self.repo.get_by_name(name) is not None:
            raise ConflictError(f"Skill '{name}' already exists")

        skill = Skill(name=name, description=data.description)
        return await self.repo.create(skill)

    async def update(self, skill_id: int, data: SkillUpdate) -> Skill:
        skill = await self.get(skill_id)
        updates = data.model_dump(exclude_unset=True)

        if updates.get("name") is not None:
            name = updates["name"].strip()
            if not name:
                raise ConflictError("Skill name cannot be empty")

            existing = await self.repo.get_by_name(name)
            if existing is not None and existing.id != skill.id:
                raise ConflictError(f"Skill '{name}' already exists")

            skill.name = name

        if "description" in updates:
            skill.description = updates["description"]

        await self.repo.session.flush()
        return skill

    async def delete(self, skill_id: int) -> None:
        skill = await self.get(skill_id)
        await self.repo.delete(skill)

    @staticmethod
    def calculate_skill_progress(experience: int) -> SkillProgress:
        """Расчёт уровня и прогресса на основе опыта."""
        level = experience // 100 + 1
        current_level_xp = (level - 1) * 100
        next_level_xp = level * 100
        progress_to_next_level = round(((experience - current_level_xp) / 100) * 100)

        return SkillProgress(
            level=level,
            current_level_xp=current_level_xp,
            next_level_xp=next_level_xp,
            progress_to_next_level=progress_to_next_level,
        )

    async def assign_skill_to_user(self, user_id: int, skill_id: int) -> UserSkillRead:
        from app.repositories.user_repo import UserRepository

        user_repo = UserRepository(self.repo.session)
        user = await user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError(f"User {user_id} not found")

        skill = await self.get(skill_id)

        existing = await self.repo.get_user_skill(user_id, skill_id)
        if existing is not None:
            raise ConflictError(f"Skill '{skill.name}' already assigned to user {user_id}")

        user_skill = await self.repo.assign_skill_to_user(user_id, skill_id)
        progress = self.calculate_skill_progress(user_skill.experience)

        return UserSkillRead(
            skill=SkillRead(id=skill.id, name=skill.name, description=skill.description),
            experience=user_skill.experience,
            level=progress.level,
            current_level_xp=progress.current_level_xp,
            next_level_xp=progress.next_level_xp,
            progress_to_next_level=progress.progress_to_next_level,
        )

    async def get_user_skills(self, user_id: int) -> list[UserSkillRead]:
        from app.repositories.user_repo import UserRepository

        user_repo = UserRepository(self.repo.session)
        user = await user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError(f"User {user_id} not found")

        user_skills = await self.repo.get_user_skills(user_id)

        result = []
        for user_skill in user_skills:
            skill = await self.repo.get(user_skill.skill_id)
            progress = self.calculate_skill_progress(user_skill.experience)

            result.append(
                UserSkillRead(
                    skill=SkillRead(id=skill.id, name=skill.name, description=skill.description),
                    experience=user_skill.experience,
                    level=progress.level,
                    current_level_xp=progress.current_level_xp,
                    next_level_xp=progress.next_level_xp,
                    progress_to_next_level=progress.progress_to_next_level,
                )
            )

        return result

    async def get_user_progress(self, user_id: int) -> UserProgressRead:
        from app.repositories.user_repo import UserRepository

        user_repo = UserRepository(self.repo.session)
        user = await user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError(f"User {user_id} not found")

        user_skills = await self.repo.get_user_skills(user_id)

        total_experience = 0
        total_level = 0
        skills_list = []

        for user_skill in user_skills:
            skill = await self.repo.get(user_skill.skill_id)
            progress = self.calculate_skill_progress(user_skill.experience)

            total_experience += user_skill.experience
            total_level += progress.level

            skills_list.append(
                UserSkillRead(
                    skill=SkillRead(id=skill.id, name=skill.name, description=skill.description),
                    experience=user_skill.experience,
                    level=progress.level,
                    current_level_xp=progress.current_level_xp,
                    next_level_xp=progress.next_level_xp,
                    progress_to_next_level=progress.progress_to_next_level,
                )
            )

        skills_count = len(user_skills)
        average_level = round(total_level / skills_count, 1) if skills_count > 0 else 0.0

        return UserProgressRead(
            user_id=user_id,
            total_experience=total_experience,
            skills_count=skills_count,
            average_level=average_level,
            skills=skills_list,
        )
