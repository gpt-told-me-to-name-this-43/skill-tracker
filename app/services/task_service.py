from datetime import datetime, timezone
from typing import Optional, Sequence

from app.models.enums import TaskStatus
from app.models.task import Task
from app.services.exceptions import NotFoundError, BadRequestError
from app.repositories.task_repo import TaskRepository
from app.schemas.task import TaskCreate, TaskUpdate
from app.services.experience import ExperienceAwarder
from app.repositories.user_repo import UserRepository


class TaskService:
    def __init__(
        self,
        task_repo: TaskRepository,
        user_repo: UserRepository,
        experience_awarder: ExperienceAwarder,
    ):
        self.task_repo = task_repo
        self.user_repo = user_repo
        self.experience_awarder = experience_awarder

    def _validate_deadline(self, deadline: Optional[datetime]) -> None:
        if deadline is None:
            return
        if deadline.tzinfo is None:
            deadline = deadline.replace(tzinfo=timezone.utc)
        if deadline <= datetime.now(timezone.utc):
            raise BadRequestError("Deadline must be in the future")

    async def _ensure_user_exists(self, user_id: Optional[int]) -> None:
        if user_id is None:
            return
        user = await self.user_repo.get_user_by_id(user_id)
        if not user:
            raise NotFoundError(f"User with id {user_id} not found")

    async def create_task(self, data: TaskCreate, creator_id: int) -> Task:
        self._validate_deadline(data.deadline)
        await self._ensure_user_exists(data.assignee_id)
        task = await self.task_repo.create_task(data, creator_id)
        await self.task_repo.session.commit()
        return task

    async def get_tasks(
        self,
        status: Optional[TaskStatus] = None,
        assignee_id: Optional[int] = None,
        difficulty: Optional[int] = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Sequence[Task]:
        return await self.task_repo.get_tasks(
            status, assignee_id, difficulty, limit, offset
        )

    async def get_task_by_id(self, task_id: int) -> Task:
        task = await self.task_repo.get_task_by_id(task_id)
        if not task:
            raise NotFoundError(f"Task with id {task_id} not found")
        return task

    async def update_task(self, task_id: int, data: TaskUpdate) -> Task:
        task = await self.get_task_by_id(task_id)
        update_data = data.model_dump(exclude_unset=True)

        if "deadline" in update_data:
            self._validate_deadline(update_data["deadline"])

        task = await self.task_repo.update_task(task, update_data)
        await self.task_repo.session.commit()
        return task

    async def change_status(self, task_id: int, new_status: TaskStatus) -> Task:
        async with self.task_repo.session.begin():
            task = await self.get_task_by_id(task_id)
            old_status = task.status

            task = await self.task_repo.set_status(task, new_status)

            if old_status != TaskStatus.done and new_status == TaskStatus.done:
                await self.experience_awarder.award_for_task(task)

        return task

    async def assign_task(
        self, task_id: int, assignee_id: Optional[int]
    ) -> Task:
        async with self.task_repo.session.begin():
            task = await self.get_task_by_id(task_id)
            await self._ensure_user_exists(assignee_id)
            task = await self.task_repo.set_assignee(task, assignee_id)
        return task
