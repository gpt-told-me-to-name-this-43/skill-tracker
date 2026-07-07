from typing import Optional

from fastapi import APIRouter, Depends, Query, status

from app.models.enums import TaskStatus
from app.schemas.task import (
    TaskAssign,
    TaskCreate,
    TaskRead,
    TaskStatusUpdate,
    TaskUpdate,
)
from app.services.task_service import TaskService


router = APIRouter(prefix="/tasks", tags=["Tasks"])


async def get_current_user_id() -> int:
    """
    Заглушка для получения текущего пользователя.
    
    TODO(epic:auth): заменить на реальную авторизацию (JWT)
    """
    return 1


@router.get("", response_model=list[TaskRead])
async def list_tasks(
    status_filter: Optional[TaskStatus] = Query(None, alias="status"),
    assignee_id: Optional[int] = None,
    difficulty: Optional[int] = None,
    limit: int = Query(100, ge=1, le=1000),
    offset: int = Query(0, ge=0),
    service: TaskService = Depends(),
):
    """Получить список задач с фильтрами."""
    return await service.get_tasks(
        status_filter, assignee_id, difficulty, limit, offset
    )


@router.post("", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
async def create_task(
    data: TaskCreate,
    service: TaskService = Depends(),
    current_user_id: int = Depends(get_current_user_id),
):
    """Создать новую задачу."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.create_task(data, creator_id=current_user_id)


@router.get("/{task_id}", response_model=TaskRead)
async def get_task(task_id: int, service: TaskService = Depends()):
    """Получить задачу по ID."""
    return await service.get_task_by_id(task_id)


@router.patch("/{task_id}", response_model=TaskRead)
async def update_task(
    task_id: int, data: TaskUpdate, service: TaskService = Depends()
):
    """Обновить обычные поля задачи (не статус и не исполнителя)."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.update_task(task_id, data)


@router.patch("/{task_id}/status", response_model=TaskRead)
async def change_task_status(
    task_id: int, data: TaskStatusUpdate, service: TaskService = Depends()
):
    """Сменить статус задачи."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.change_status(task_id, data.status)


@router.patch("/{task_id}/assign", response_model=TaskRead)
async def assign_task(
    task_id: int, data: TaskAssign, service: TaskService = Depends()
):
    """Назначить или снять исполнителя задачи."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.assign_task(task_id, data.assignee_id)