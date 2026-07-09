from fastapi import APIRouter, Query, status

from app.api.deps import CurrentUser, ExperienceServiceDep, TaskServiceDep
from app.models.enums import TaskStatus
from app.schemas.experience import TaskSkillRead, TaskSkillsSet
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
    status: TaskStatus | None = None,
    assignee_id: int | None = None,
    difficulty: int | None = Query(None, ge=1, le=5),
    limit: int = Query(100, ge=1, le=1000),
    offset: int = Query(0, ge=0),
):
    """Получить список задач с фильтрами."""
    return await service.get_tasks(
        status=status,
        assignee_id=assignee_id,
        difficulty=difficulty,
        limit=limit,
        offset=offset,
    )


@router.post("/tasks", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
async def create_task(
    data: TaskCreate,
    service: TaskServiceDep,
    current_user: CurrentUser,
):
    """Создать новую задачу от имени текущего пользователя."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.create_task(data, creator_id=current_user.id)


@router.get("/tasks/{task_id}", response_model=TaskRead)
async def get_task(
    task_id: int,
    service: TaskServiceDep,
):
    """Получить задачу по ID."""
    return await service.get_task_by_id(task_id)


@router.get("/tasks/{task_id}/skills", response_model=list[TaskSkillRead])
async def get_task_skills(
    task_id: int,
    service: ExperienceServiceDep,
):
    """Получить награды задачи по навыкам."""
    return await service.get_task_skills(task_id)


@router.put("/tasks/{task_id}/skills", response_model=list[TaskSkillRead])
async def set_task_skills(
    task_id: int,
    data: TaskSkillsSet,
    service: ExperienceServiceDep,
):
    """Полностью заменить награды задачи по навыкам."""
    # TODO(epic:auth-rbac): restrict task reward updates by creator/admin
    return await service.set_task_skills(task_id, data)


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


@router.patch("/tasks/{task_id}/approve", response_model=TaskRead)
async def approve_task(
    task_id: int,
    service: TaskServiceDep,
    current_user: CurrentUser,
):
    """Approve a review task before it can be moved to done."""
    # TODO(epic:auth-rbac): restrict approval by reviewer/admin.
    return await service.approve_task(task_id, current_user.id)


@router.patch("/tasks/{task_id}/assign", response_model=TaskRead)
async def assign_task(
    task_id: int,
    data: TaskAssign,
    service: TaskServiceDep,
):
    """Назначить или снять исполнителя задачи."""
    # TODO(epic:auth): restrict task updates by creator/assignee/admin
    return await service.assign_task(task_id, data.assignee_id)
