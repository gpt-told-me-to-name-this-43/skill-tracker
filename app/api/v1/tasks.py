from fastapi import APIRouter, status

from app.api.deps import TaskServiceDep
from app.schemas.task import (
    TaskAssign,
    TaskCreate,
    TaskRead,
    TaskStatusUpdate,
    TaskUpdate,
)

router = APIRouter()


@router.get("/tasks", response_model=list[TaskRead])
async def list_tasks(
    service: TaskServiceDep,
):
    """Получить список задач."""
    # TODO: добавить фильтры status, assignee_id, difficulty
    return await service.get_tasks()


@router.post("/tasks", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
async def create_task(
    data: TaskCreate,
    service: TaskServiceDep,
):
    """Создать новую задачу."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    # TODO: получить current_user_id из авторизации
    creator_id = 1  # заглушка
    return await service.create_task(data, creator_id=creator_id)


@router.get("/tasks/{task_id}", response_model=TaskRead)
async def get_task(
    task_id: int,
    service: TaskServiceDep,
):
    """Получить задачу по ID."""
    return await service.get_task_by_id(task_id)


@router.patch("/tasks/{task_id}", response_model=TaskRead)
async def update_task(
    task_id: int,
    data: TaskUpdate,
    service: TaskServiceDep,
):
    """Обновить обычные поля задачи (не статус и не исполнителя)."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.update_task(task_id, data)


@router.patch("/tasks/{task_id}/status", response_model=TaskRead)
async def change_task_status(
    task_id: int,
    data: TaskStatusUpdate,
    service: TaskServiceDep,
):
    """Сменить статус задачи."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.change_status(task_id, data.status)


@router.patch("/tasks/{task_id}/assign", response_model=TaskRead)
async def assign_task(
    task_id: int,
    data: TaskAssign,
    service: TaskServiceDep,
):
    """Назначить или снять исполнителя задачи."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.assign_task(task_id, data.assignee_id)
