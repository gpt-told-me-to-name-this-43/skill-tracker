from collections.abc import Sequence

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.enums import TaskStatus
from app.models.task import Task


class TaskRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_tasks(
        self,
        status: TaskStatus | None = None,
        assignee_id: int | None = None,
        difficulty: int | None = None,
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

    async def get_task_by_id(self, task_id: int) -> Task | None:
        result = await self.session.execute(select(Task).where(Task.id == task_id))
        return result.scalar_one_or_none()

    async def create_task(self, fields: dict, creator_id: int) -> Task:
        task = Task(**fields, creator_id=creator_id)
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

    async def set_status(
        self,
        task: Task,
        status: TaskStatus,
        clear_approval: bool = False,
    ) -> Task:
        task.status = status
        if clear_approval:
            task.approved_by_id = None
            task.approved_at = None
        await self.session.flush()
        await self.session.refresh(task)
        return task

    async def set_assignee(self, task: Task, assignee_id: int | None) -> Task:
        task.assignee_id = assignee_id
        await self.session.flush()
        await self.session.refresh(task)
        return task

    async def approve_task(self, task: Task, approver_id: int, approved_at) -> Task:
        task.approved_by_id = approver_id
        task.approved_at = approved_at
        await self.session.flush()
        await self.session.refresh(task)
        return task
