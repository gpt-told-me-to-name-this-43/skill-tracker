from typing import Sequence, Optional

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.task import Task
from app.models.enums import TaskStatus
from app.schemas.task import TaskCreate


class TaskRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_tasks(
        self,
        status: Optional[TaskStatus] = None,
        assignee_id: Optional[int] = None,
        difficulty: Optional[int] = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Sequence[Task]:
        query = select(Task)
        if status is not None:
            query = query.where(Task.status == status)
        if assignee_id is not None:
            query = query.where(Task.assignee_id == assignee_id)
        if difficulty is not None:
            query = query.where(Task.difficulty == difficulty)

        query = query.order_by(Task.id.desc()).limit(limit).offset(offset)
        result = await self.session.execute(query)
        return result.scalars().all()

    async def get_task_by_id(self, task_id: int) -> Optional[Task]:
        result = await self.session.execute(
            select(Task).where(Task.id == task_id)
        )
        return result.scalar_one_or_none()

    async def create_task(self, data: TaskCreate, creator_id: int) -> Task:
        task = Task(**data.model_dump(), creator_id=creator_id)
        self.session.add(task)
        await self.session.flush()
        await self.session.refresh(task)
        return task

    async def update_task(self, task: Task, fields: dict) -> Task:
        for key, value in fields.items():
            setattr(task, key, value)
        await self.session.flush()
        await self.session.refresh(task)
        return task

    async def set_status(self, task: Task, status: TaskStatus) -> Task:
        task.status = status
        await self.session.flush()
        await self.session.refresh(task)
        return task

    async def set_assignee(self, task: Task, assignee_id: Optional[int]) -> Task:
        task.assignee_id = assignee_id
        await self.session.flush()
        await self.session.refresh(task)
        return task
