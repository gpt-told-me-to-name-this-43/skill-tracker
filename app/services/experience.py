import logging
from collections.abc import Sequence
from typing import Protocol

from app.models.enums import TaskStatus
from app.models.experience import ExperienceLog
from app.models.task import Task, TaskSkill
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.schemas.experience import TaskSkillsSet
from app.services.exceptions import NotFoundError

logger = logging.getLogger(__name__)


class ExperienceAwarder(Protocol):
    """
    Контракт (интерфейс) для начисления опыта за задачу.
    Tasks-блок НЕ знает про реализацию Experience-блока.
    Он знает только этот контракт!!!!!
    Когда Experience-блок будет готов, он предоставит свою реализацию
    этого протокола, и Tasks автоматически начнёт начислять опыт.
    """

    async def award_for_task(self, task: Task) -> None:
        """
        Начислить опыт за выполнение задачи.
        """
        ...


class NoOpAwarder:
    """
    Заглушка-реализация ExperienceAwarder.
    Используется ПОКА Experience-блок не готов.
    Ничего не делает, просто возвращает None.
    """

    async def award_for_task(self, task: Task) -> None:
        """Ничего не делаем — заглушка."""
        return None


class DefaultExperienceAwarder:
    def __init__(self, repo: ExperienceRepository) -> None:
        self.repo = repo

    async def award_for_task(self, task: Task) -> None:
        if task.assignee_id is None:
            logger.warning("Task %s is done without assignee; skipping XP award", task.id)
            return

        task_skills = await self.repo.get_task_skills(task.id)
        if not task_skills:
            return

        for task_skill in task_skills:
            existing_log = await self.repo.get_log_entry(
                task_id=task.id,
                user_id=task.assignee_id,
                skill_id=task_skill.skill_id,
            )
            if existing_log is not None:
                continue

            user_skill = await self.repo.get_or_create_user_skill(
                user_id=task.assignee_id,
                skill_id=task_skill.skill_id,
            )
            await self.repo.increment_user_skill_experience(user_skill, task_skill.exp_reward)
            await self.repo.create_log_entry(
                user_id=task.assignee_id,
                skill_id=task_skill.skill_id,
                task_id=task.id,
                amount=task_skill.exp_reward,
            )


class ExperienceService:
    def __init__(
        self,
        experience_repo: ExperienceRepository,
        task_repo: TaskRepository,
        skill_repo: SkillRepository,
        user_repo: UserRepository,
        experience_awarder: ExperienceAwarder | None = None,
    ) -> None:
        self.experience_repo = experience_repo
        self.task_repo = task_repo
        self.skill_repo = skill_repo
        self.user_repo = user_repo
        self.experience_awarder = experience_awarder or DefaultExperienceAwarder(experience_repo)

    async def get_task_skills(self, task_id: int) -> Sequence[TaskSkill]:
        await self._ensure_task_exists(task_id)
        return await self.experience_repo.get_task_skills(task_id)

    async def set_task_skills(self, task_id: int, data: TaskSkillsSet) -> Sequence[TaskSkill]:
        task = await self._ensure_task_exists(task_id)
        for item in data.skills:
            await self._ensure_skill_exists(item.skill_id)

        items = [item.model_dump() for item in data.skills]
        task_skills = await self.experience_repo.set_task_skills(task_id, items)

        # Награды, назначенные уже завершённой задаче, начисляются сразу.
        # Awarder идемпотентен по (task, user, skill): ранее начисленное не дублируется.
        if task.status == TaskStatus.done:
            await self.experience_awarder.award_for_task(task)

        return task_skills

    async def get_user_log(
        self,
        user_id: int,
        limit: int,
        offset: int,
    ) -> Sequence[ExperienceLog]:
        await self._ensure_user_exists(user_id)
        return await self.experience_repo.get_user_log(user_id, limit, offset)

    async def _ensure_task_exists(self, task_id: int) -> Task:
        task = await self.task_repo.get_task_by_id(task_id)
        if task is None:
            raise NotFoundError(f"Task with id {task_id} not found")
        return task

    async def _ensure_skill_exists(self, skill_id: int) -> None:
        skill = await self.skill_repo.get(skill_id)
        if skill is None:
            raise NotFoundError(f"Skill {skill_id} not found")

    async def _ensure_user_exists(self, user_id: int) -> None:
        user = await self.user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError(f"User {user_id} not found")
