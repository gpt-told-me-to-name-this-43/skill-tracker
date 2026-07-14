from collections.abc import Sequence

from sqlalchemy import delete, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.enums import TaskStatus
from app.models.task import Label, Task, TaskAttachment, TaskLabel, TaskRelation


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
        query = select(Task).options(
            selectinload(Task.creator),
            selectinload(Task.assignee),
            selectinload(Task.labels).selectinload(TaskLabel.label),
            selectinload(Task.attachments),
            selectinload(Task.left_relations).selectinload(TaskRelation.right_task),
            selectinload(Task.right_relations).selectinload(TaskRelation.left_task),
        )
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
        result = await self.session.execute(
            select(Task)
            .options(
                selectinload(Task.creator),
                selectinload(Task.assignee),
                selectinload(Task.labels).selectinload(TaskLabel.label),
                selectinload(Task.attachments).selectinload(TaskAttachment.created_by),
                selectinload(Task.left_relations).selectinload(TaskRelation.right_task),
                selectinload(Task.right_relations).selectinload(TaskRelation.left_task),
            )
            .where(Task.id == task_id)
        )
        return result.scalar_one_or_none()

    async def get_task_with_labels(self, task_id: int) -> Task | None:
        result = await self.session.execute(
            select(Task).options(selectinload(Task.labels)).where(Task.id == task_id)
        )
        return result.scalar_one_or_none()

    async def get_task_by_github_issue_number(self, issue_number: int) -> Task | None:
        result = await self.session.execute(
            select(Task)
            .options(selectinload(Task.labels).selectinload(TaskLabel.label))
            .where(Task.github_issue_number == issue_number)
        )
        return result.scalar_one_or_none()

    async def get_tasks_by_ids(self, task_ids: Sequence[int]) -> list[Task]:
        if not task_ids:
            return []
        result = await self.session.execute(select(Task).where(Task.id.in_(task_ids)))
        return list(result.scalars().all())

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

    async def list_labels(self) -> list[Label]:
        result = await self.session.execute(select(Label).order_by(Label.name))
        return list(result.scalars().all())

    async def get_label_by_name_ci(self, name: str) -> Label | None:
        result = await self.session.execute(
            select(Label).where(func.lower(Label.name) == name.lower())
        )
        return result.scalar_one_or_none()

    async def get_labels_by_ids(self, label_ids: Sequence[int]) -> list[Label]:
        if not label_ids:
            return []
        result = await self.session.execute(select(Label).where(Label.id.in_(label_ids)))
        return list(result.scalars().all())

    async def create_label(self, name: str, color: str | None) -> Label:
        label = Label(name=name, color=color)
        self.session.add(label)
        await self.session.flush()
        await self.session.refresh(label)
        return label

    async def replace_task_labels(self, task_id: int, label_ids: Sequence[int]) -> None:
        await self.session.execute(delete(TaskLabel).where(TaskLabel.task_id == task_id))
        for label_id in label_ids:
            self.session.add(TaskLabel(task_id=task_id, label_id=label_id))
        await self.session.flush()

    async def list_attachments(self, task_id: int) -> list[TaskAttachment]:
        result = await self.session.execute(
            select(TaskAttachment)
            .options(selectinload(TaskAttachment.created_by))
            .where(TaskAttachment.task_id == task_id)
            .order_by(TaskAttachment.id)
        )
        return list(result.scalars().all())

    async def get_attachment(self, task_id: int, attachment_id: int) -> TaskAttachment | None:
        result = await self.session.execute(
            select(TaskAttachment).where(
                TaskAttachment.task_id == task_id,
                TaskAttachment.id == attachment_id,
            )
        )
        return result.scalar_one_or_none()

    async def create_attachment(
        self,
        task_id: int,
        name: str,
        url: str,
        created_by_id: int,
    ) -> TaskAttachment:
        attachment = TaskAttachment(
            task_id=task_id,
            name=name,
            url=url,
            created_by_id=created_by_id,
        )
        self.session.add(attachment)
        await self.session.flush()
        await self.session.refresh(attachment, attribute_names=["created_by"])
        return attachment

    async def delete_attachment(self, attachment: TaskAttachment) -> None:
        await self.session.delete(attachment)
        await self.session.flush()

    async def list_related_tasks(self, task_id: int) -> list[Task]:
        result = await self.session.execute(
            select(TaskRelation)
            .options(
                selectinload(TaskRelation.left_task),
                selectinload(TaskRelation.right_task),
            )
            .where(
                or_(
                    TaskRelation.left_task_id == task_id,
                    TaskRelation.right_task_id == task_id,
                )
            )
        )
        relations = result.scalars().all()
        return [
            relation.right_task if relation.left_task_id == task_id else relation.left_task
            for relation in relations
        ]

    async def replace_related_tasks(self, task_id: int, related_task_ids: Sequence[int]) -> None:
        await self.session.execute(
            delete(TaskRelation).where(
                or_(
                    TaskRelation.left_task_id == task_id,
                    TaskRelation.right_task_id == task_id,
                )
            )
        )
        for related_task_id in related_task_ids:
            left_task_id = min(task_id, related_task_id)
            right_task_id = max(task_id, related_task_id)
            self.session.add(TaskRelation(left_task_id=left_task_id, right_task_id=right_task_id))
        await self.session.flush()
