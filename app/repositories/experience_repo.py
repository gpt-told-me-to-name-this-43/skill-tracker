from collections.abc import Sequence

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.experience import ExperienceLog
from app.models.task import TaskSkill
from app.models.user import UserSkill


class ExperienceRepository:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def get_task_skills(self, task_id: int) -> Sequence[TaskSkill]:
        result = await self.session.execute(
            select(TaskSkill)
            .options(selectinload(TaskSkill.skill))
            .where(TaskSkill.task_id == task_id)
            .order_by(TaskSkill.id)
        )
        return result.scalars().all()

    async def set_task_skills(
        self, task_id: int, items: Sequence[dict[str, int]]
    ) -> Sequence[TaskSkill]:
        await self.session.execute(delete(TaskSkill).where(TaskSkill.task_id == task_id))
        for item in items:
            self.session.add(
                TaskSkill(
                    task_id=task_id,
                    skill_id=item["skill_id"],
                    exp_reward=item["exp_reward"],
                )
            )
        await self.session.flush()
        return await self.get_task_skills(task_id)

    async def get_log_entry(
        self,
        task_id: int,
        user_id: int,
        skill_id: int,
    ) -> ExperienceLog | None:
        result = await self.session.execute(
            select(ExperienceLog).where(
                ExperienceLog.task_id == task_id,
                ExperienceLog.user_id == user_id,
                ExperienceLog.skill_id == skill_id,
            )
        )
        return result.scalar_one_or_none()

    async def create_log_entry(
        self,
        user_id: int,
        skill_id: int,
        task_id: int,
        amount: int,
    ) -> ExperienceLog:
        log_entry = ExperienceLog(
            user_id=user_id,
            skill_id=skill_id,
            task_id=task_id,
            amount=amount,
        )
        self.session.add(log_entry)
        await self.session.flush()
        await self.session.refresh(log_entry)
        return log_entry

    async def get_user_log(self, user_id: int, limit: int, offset: int) -> Sequence[ExperienceLog]:
        result = await self.session.execute(
            select(ExperienceLog)
            .where(ExperienceLog.user_id == user_id)
            .order_by(ExperienceLog.created_at.desc(), ExperienceLog.id.desc())
            .limit(limit)
            .offset(offset)
        )
        return result.scalars().all()

    async def get_or_create_user_skill(self, user_id: int, skill_id: int) -> UserSkill:
        result = await self.session.execute(
            select(UserSkill).where(UserSkill.user_id == user_id, UserSkill.skill_id == skill_id)
        )
        user_skill = result.scalar_one_or_none()
        if user_skill is not None:
            return user_skill

        user_skill = UserSkill(user_id=user_id, skill_id=skill_id, experience=0)
        self.session.add(user_skill)
        await self.session.flush()
        await self.session.refresh(user_skill)
        return user_skill

    async def increment_user_skill_experience(
        self, user_skill: UserSkill, amount: int
    ) -> UserSkill:
        user_skill.experience += amount
        await self.session.flush()
        await self.session.refresh(user_skill)
        return user_skill
