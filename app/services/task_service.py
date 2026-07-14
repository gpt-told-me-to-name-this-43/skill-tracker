from collections.abc import Sequence
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

from app.core.config import settings
from app.core.time_utils import to_naive_utc
from app.models.enums import TaskStatus
from app.models.task import Label, Task, TaskAttachment
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.schemas.label import LabelCreate
from app.schemas.task import TaskAttachmentCreate, TaskCreate, TaskUpdate
from app.services.exceptions import BadRequestError, ConflictError, NotFoundError
from app.services.experience import ExperienceAwarder


def _serialize_user(user) -> dict | None:
    if user is None:
        return None
    return {
        "id": user.id,
        "username": user.username,
        "avatar_url": user.avatar_url,
        "position": user.position,
        "member_status": user.member_status,
        "github_login": user.github_login,
        "is_placeholder": user.is_placeholder,
    }


def _serialize_label(label: Label) -> dict:
    return {
        "id": label.id,
        "name": label.name,
        "color": label.color,
        "created_at": label.created_at,
        "updated_at": label.updated_at,
    }


def _serialize_related_task(task: Task) -> dict:
    return {
        "id": task.id,
        "title": task.title,
        "status": task.status,
    }


def _serialize_attachment(attachment: TaskAttachment) -> dict:
    return {
        "id": attachment.id,
        "name": attachment.name,
        "url": attachment.url,
        "created_by": _serialize_user(attachment.created_by),
        "created_at": attachment.created_at,
    }


class TaskService:
    def _validate_status_transition(
        self,
        old_status: TaskStatus,
        new_status: TaskStatus,
    ) -> None:
        if old_status == new_status:
            return

        if new_status == TaskStatus.done and old_status != TaskStatus.review:
            raise BadRequestError("Task can be moved to done only from review")

    def _should_clear_approval(self, old_status: TaskStatus, new_status: TaskStatus) -> bool:
        if old_status == new_status:
            return False
        if new_status == TaskStatus.review:
            return True
        return old_status in {TaskStatus.review, TaskStatus.done} and new_status != TaskStatus.done

    def __init__(
        self,
        task_repo: TaskRepository,
        user_repo: UserRepository,
        experience_awarder: ExperienceAwarder,
    ):
        self.task_repo = task_repo
        self.user_repo = user_repo
        self.experience_awarder = experience_awarder

    def serialize_task_list_item(self, task: Task) -> dict:
        labels = [_serialize_label(task_label.label) for task_label in task.labels]
        related_count = len(task.left_relations) + len(task.right_relations)
        return {
            "id": task.id,
            "title": task.title,
            "status": task.status,
            "difficulty": task.difficulty,
            "deadline": task.deadline,
            "creator": _serialize_user(task.creator),
            "assignee": _serialize_user(task.assignee),
            "labels": labels,
            "attachments_count": len(task.attachments),
            "related_tasks_count": related_count,
            "github_issue_number": task.github_issue_number,
            "github_url": (
                f"https://github.com/{settings.github_repo}/issues/{task.github_issue_number}"
                if task.github_issue_number is not None
                else None
            ),
            "created_at": task.created_at,
            "updated_at": task.updated_at,
        }

    def serialize_task_detail(self, task: Task) -> dict:
        data = self.serialize_task_list_item(task)
        related_tasks = [relation.right_task for relation in task.left_relations] + [
            relation.left_task for relation in task.right_relations
        ]
        data.update(
            {
                "description": task.description,
                "attachments": [
                    _serialize_attachment(attachment) for attachment in task.attachments
                ],
                "related_tasks": [_serialize_related_task(item) for item in related_tasks],
                "creator_id": task.creator_id,
                "assignee_id": task.assignee_id,
                "approved_by_id": task.approved_by_id,
                "approved_at": task.approved_at,
            }
        )
        return data

    def _validate_deadline(self, deadline: datetime | None) -> datetime | None:
        """
        Проверяет, что deadline в будущем.
        Возвращает deadline без timezone для совместимости с БД.
        """
        if deadline is None:
            return None
        if deadline.tzinfo is None:
            deadline = deadline.replace(tzinfo=UTC)
        if deadline <= datetime.now(UTC):
            raise BadRequestError("Deadline must be in the future")
        return to_naive_utc(deadline)

    async def _ensure_user_exists(self, user_id: int | None) -> None:
        """Проверяет существование пользователя."""
        if user_id is None:
            return
        user = await self.user_repo.get_user_by_id(user_id)
        if not user:
            raise NotFoundError(f"User with id {user_id} not found")

    async def create_task(self, data: TaskCreate, creator_id: int) -> dict:
        """Создаёт новую задачу."""
        clean_deadline = self._validate_deadline(data.deadline)

        await self._ensure_user_exists(creator_id)
        await self._ensure_user_exists(data.assignee_id)

        fields = data.model_dump()
        fields["deadline"] = clean_deadline

        task = await self.task_repo.create_task(fields, creator_id=creator_id)
        return self.serialize_task_detail(await self.get_task_model_by_id(task.id))

    async def get_tasks(
        self,
        status: TaskStatus | None = None,
        assignee_id: int | None = None,
        difficulty: int | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Sequence[dict]:
        """Получает список задач с фильтрами."""
        tasks = await self.task_repo.get_tasks(status, assignee_id, difficulty, limit, offset)
        return [self.serialize_task_list_item(task) for task in tasks]

    async def get_task_model_by_id(self, task_id: int) -> Task:
        """Получает задачу по ID."""
        task = await self.task_repo.get_task_by_id(task_id)
        if not task:
            raise NotFoundError(f"Task with id {task_id} not found")
        return task

    async def get_task_by_id(self, task_id: int) -> dict:
        return self.serialize_task_detail(await self.get_task_model_by_id(task_id))

    async def update_task(self, task_id: int, data: TaskUpdate) -> dict:
        """Обновляет обычные поля задачи (не статус и не исполнителя)."""
        task = await self.get_task_model_by_id(task_id)
        update_data = data.model_dump(exclude_unset=True)

        if "deadline" in update_data:
            update_data["deadline"] = self._validate_deadline(update_data["deadline"])

        task = await self.task_repo.update_task(task, update_data)
        return self.serialize_task_detail(await self.get_task_model_by_id(task.id))

    async def change_status(self, task_id: int, new_status: TaskStatus) -> dict:
        """
        Меняет статус задачи.

        Если переход non-done -> done, вызывает хук начисления опыта.
        Атомарно: если award_for_task упадёт, статус не изменится.
        """
        task = await self.get_task_model_by_id(task_id)
        old_status = task.status
        self._validate_status_transition(old_status, new_status)

        moving_to_done_from_review = (
            old_status == TaskStatus.review and new_status == TaskStatus.done
        )
        if moving_to_done_from_review and not task.approved_at:
            raise BadRequestError("Task must be approved before moving to done")

        task = await self.task_repo.set_status(
            task,
            new_status,
            clear_approval=self._should_clear_approval(old_status, new_status),
        )

        if old_status != TaskStatus.done and new_status == TaskStatus.done:
            await self.experience_awarder.award_for_task(task)

        return self.serialize_task_detail(await self.get_task_model_by_id(task.id))

    async def assign_task(self, task_id: int, assignee_id: int | None) -> dict:
        """
        Назначает или снимает исполнителя задачи.

        Разрешено даже для задач в статусе done (MVP).
        XP не переносится и не отзывается.
        """
        task = await self.get_task_model_by_id(task_id)
        await self._ensure_user_exists(assignee_id)
        task = await self.task_repo.set_assignee(task, assignee_id)
        return self.serialize_task_detail(await self.get_task_model_by_id(task.id))

    async def approve_task(self, task_id: int, approver_id: int) -> dict:
        task = await self.get_task_model_by_id(task_id)
        await self._ensure_user_exists(approver_id)

        if task.status != TaskStatus.review:
            raise BadRequestError("Only tasks in review can be approved")

        approved_at = datetime.now(UTC).replace(tzinfo=None)
        task = await self.task_repo.approve_task(task, approver_id, approved_at)
        return self.serialize_task_detail(await self.get_task_model_by_id(task.id))

    async def list_labels(self) -> list[Label]:
        return await self.task_repo.list_labels()

    async def create_label(self, data: LabelCreate) -> Label:
        existing = await self.task_repo.get_label_by_name_ci(data.name)
        if existing is not None:
            raise ConflictError("Label name already exists")
        return await self.task_repo.create_label(data.name, data.color)

    async def set_task_labels(self, task_id: int, label_ids: list[int]) -> dict:
        await self.get_task_model_by_id(task_id)
        labels = await self.task_repo.get_labels_by_ids(label_ids)
        if len(labels) != len(label_ids):
            raise NotFoundError("One or more labels were not found")
        await self.task_repo.replace_task_labels(task_id, label_ids)
        return self.serialize_task_detail(await self.get_task_model_by_id(task_id))

    async def list_attachments(self, task_id: int) -> list[dict]:
        await self.get_task_model_by_id(task_id)
        attachments = await self.task_repo.list_attachments(task_id)
        return [_serialize_attachment(attachment) for attachment in attachments]

    async def create_attachment(
        self,
        task_id: int,
        data: TaskAttachmentCreate,
        created_by_id: int,
    ) -> dict:
        await self.get_task_model_by_id(task_id)
        await self._ensure_user_exists(created_by_id)
        attachment = await self.task_repo.create_attachment(
            task_id,
            data.name,
            data.url,
            created_by_id,
        )
        return _serialize_attachment(attachment)

    async def create_uploaded_attachment(
        self,
        task_id: int,
        filename: str,
        content: bytes,
        created_by_id: int,
    ) -> dict:
        await self.get_task_model_by_id(task_id)
        await self._ensure_user_exists(created_by_id)
        if not content:
            raise BadRequestError("Uploaded file is empty")

        original_name = Path(filename).name or "attachment"
        safe_name = "".join(
            char if char.isalnum() or char in {".", "-", "_"} else "-" for char in original_name
        ).strip(".-")
        if not safe_name:
            safe_name = "attachment"

        upload_dir = Path(settings.upload_dir) / "task-attachments"
        upload_dir.mkdir(parents=True, exist_ok=True)
        stored_name = f"{uuid4().hex}-{safe_name}"
        file_path = upload_dir / stored_name
        file_path.write_bytes(content)

        attachment = await self.task_repo.create_attachment(
            task_id,
            original_name,
            f"/uploads/task-attachments/{stored_name}",
            created_by_id,
        )
        return _serialize_attachment(attachment)

    async def delete_attachment(self, task_id: int, attachment_id: int) -> None:
        await self.get_task_model_by_id(task_id)
        attachment = await self.task_repo.get_attachment(task_id, attachment_id)
        if attachment is None:
            raise NotFoundError("Attachment not found")
        await self.task_repo.delete_attachment(attachment)

    async def list_related_tasks(self, task_id: int) -> list[dict]:
        await self.get_task_model_by_id(task_id)
        tasks = await self.task_repo.list_related_tasks(task_id)
        return [_serialize_related_task(task) for task in tasks]

    async def set_related_tasks(self, task_id: int, task_ids: list[int]) -> list[dict]:
        await self.get_task_model_by_id(task_id)
        if task_id in task_ids:
            raise BadRequestError("Task cannot be related to itself")
        related_tasks = await self.task_repo.get_tasks_by_ids(task_ids)
        if len(related_tasks) != len(task_ids):
            raise NotFoundError("One or more related tasks were not found")
        await self.task_repo.replace_related_tasks(task_id, task_ids)
        return await self.list_related_tasks(task_id)
