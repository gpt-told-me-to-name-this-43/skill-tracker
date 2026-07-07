from collections.abc import Sequence
from datetime import UTC, datetime

from app.models.enums import TaskStatus
from app.models.task import Task
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.schemas.task import TaskCreate, TaskUpdate
from app.services.exceptions import BadRequestError, NotFoundError
from app.services.experience import ExperienceAwarder


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
        # Убираем timezone для совместимости с TIMESTAMP WITHOUT TIME ZONE
        return deadline.replace(tzinfo=None)

    async def _ensure_user_exists(self, user_id: int | None) -> None:
        """Проверяет существование пользователя."""
        if user_id is None:
            return
        user = await self.user_repo.get(user_id)
        if not user:
            raise NotFoundError(f"User with id {user_id} not found")

    async def create_task(self, data: TaskCreate, creator_id: int) -> Task:
        """Создаёт новую задачу."""
        # Валидируем deadline и очищаем от timezone
        clean_deadline = self._validate_deadline(data.deadline)

        await self._ensure_user_exists(creator_id)
        await self._ensure_user_exists(data.assignee_id)

        # Готовим поля для репозитория
        fields = data.model_dump()
        fields["deadline"] = clean_deadline

        # Repository сам сделает flush, commit делает dependency на уровне запроса
        return await self.task_repo.create_task(fields, creator_id=creator_id)

    async def get_tasks(
        self,
        status: TaskStatus | None = None,
        assignee_id: int | None = None,
        difficulty: int | None = None,
        limit: int = 100,
        offset: int = 0,
    ) -> Sequence[Task]:
        """Получает список задач с фильтрами."""
        return await self.task_repo.get_tasks(status, assignee_id, difficulty, limit, offset)

    async def get_task_by_id(self, task_id: int) -> Task:
        """Получает задачу по ID."""
        task = await self.task_repo.get_task_by_id(task_id)
        if not task:
            raise NotFoundError(f"Task with id {task_id} not found")
        return task

    async def update_task(self, task_id: int, data: TaskUpdate) -> Task:
        """Обновляет обычные поля задачи (не статус и не исполнителя)."""
        task = await self.get_task_by_id(task_id)
        update_data = data.model_dump(exclude_unset=True)

        if "deadline" in update_data:
            update_data["deadline"] = self._validate_deadline(update_data["deadline"])

        return await self.task_repo.update_task(task, update_data)

    async def change_status(self, task_id: int, new_status: TaskStatus) -> Task:
        """
        Меняет статус задачи.

        Если переход non-done -> done, вызывает хук начисления опыта.
        Атомарно: если award_for_task упадёт, статус не изменится.
        """
        task = await self.get_task_by_id(task_id)
        old_status = task.status

        task = await self.task_repo.set_status(task, new_status)

        # Хук опыта вызывается ТОЛЬКО при переходе non-done -> done
        if old_status != TaskStatus.done and new_status == TaskStatus.done:
            # TODO(epic:experience): replace NoOpAwarder with real ExperienceAwarder
            await self.experience_awarder.award_for_task(task)

        return task

    async def assign_task(self, task_id: int, assignee_id: int | None) -> Task:
        """
        Назначает или снимает исполнителя задачи.

        Разрешено даже для задач в статусе done (MVP).
        XP не переносится и не отзывается.
        """
        task = await self.get_task_by_id(task_id)
        await self._ensure_user_exists(assignee_id)
        return await self.task_repo.set_assignee(task, assignee_id)
