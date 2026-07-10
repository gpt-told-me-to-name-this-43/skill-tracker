from fastapi import APIRouter, Query, status

from app.api.deps import CurrentUser, ExperienceServiceDep, TaskServiceDep
from app.models.enums import TaskStatus
from app.schemas.experience import TaskSkillRead, TaskSkillsSet
from app.schemas.label import LabelCreate, LabelRead, TaskLabelsSet
from app.schemas.task import (
    RelatedTaskRead,
    TaskAssign,
    TaskAttachmentCreate,
    TaskAttachmentRead,
    TaskCreate,
    TaskDetail,
    TaskListItem,
    TaskRelatedSet,
    TaskStatusUpdate,
    TaskUpdate,
)

router = APIRouter()


@router.get("/tasks", response_model=list[TaskListItem])
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


@router.post("/tasks", response_model=TaskDetail, status_code=status.HTTP_201_CREATED)
async def create_task(
    data: TaskCreate,
    service: TaskServiceDep,
    current_user: CurrentUser,
):
    """Создать новую задачу от имени текущего пользователя."""
    return await service.create_task(data, creator_id=current_user.id)


@router.get("/tasks/{task_id}", response_model=TaskDetail)
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
    return await service.set_task_skills(task_id, data)


@router.patch("/tasks/{task_id}", response_model=TaskDetail)
async def update_task(
    task_id: int,
    data: TaskUpdate,
    service: TaskServiceDep,
):
    """Обновить обычные поля задачи (не статус и не исполнителя)."""
    return await service.update_task(task_id, data)


@router.patch("/tasks/{task_id}/status", response_model=TaskDetail)
async def change_task_status(
    task_id: int,
    data: TaskStatusUpdate,
    service: TaskServiceDep,
):
    """Сменить статус задачи."""
    return await service.change_status(task_id, data.status)


@router.patch("/tasks/{task_id}/approve", response_model=TaskDetail)
async def approve_task(
    task_id: int,
    service: TaskServiceDep,
    current_user: CurrentUser,
):
    """Approve a review task before it can be moved to done."""
    return await service.approve_task(task_id, current_user.id)


@router.patch("/tasks/{task_id}/assign", response_model=TaskDetail)
async def assign_task(
    task_id: int,
    data: TaskAssign,
    service: TaskServiceDep,
):
    """Назначить или снять исполнителя задачи."""
    return await service.assign_task(task_id, data.assignee_id)


@router.get("/labels", response_model=list[LabelRead])
async def list_labels(service: TaskServiceDep):
    return await service.list_labels()


@router.post("/labels", response_model=LabelRead, status_code=status.HTTP_201_CREATED)
async def create_label(data: LabelCreate, service: TaskServiceDep):
    return await service.create_label(data)


@router.put("/tasks/{task_id}/labels", response_model=TaskDetail)
async def set_task_labels(task_id: int, data: TaskLabelsSet, service: TaskServiceDep):
    return await service.set_task_labels(task_id, data.label_ids)


@router.get("/tasks/{task_id}/attachments", response_model=list[TaskAttachmentRead])
async def list_attachments(task_id: int, service: TaskServiceDep):
    return await service.list_attachments(task_id)


@router.post(
    "/tasks/{task_id}/attachments",
    response_model=TaskAttachmentRead,
    status_code=status.HTTP_201_CREATED,
)
async def create_attachment(
    task_id: int,
    data: TaskAttachmentCreate,
    service: TaskServiceDep,
    current_user: CurrentUser,
):
    return await service.create_attachment(task_id, data, current_user.id)


@router.delete(
    "/tasks/{task_id}/attachments/{attachment_id}", status_code=status.HTTP_204_NO_CONTENT
)
async def delete_attachment(task_id: int, attachment_id: int, service: TaskServiceDep):
    await service.delete_attachment(task_id, attachment_id)


@router.get("/tasks/{task_id}/related", response_model=list[RelatedTaskRead])
async def list_related_tasks(task_id: int, service: TaskServiceDep):
    return await service.list_related_tasks(task_id)


@router.put("/tasks/{task_id}/related", response_model=list[RelatedTaskRead])
async def set_related_tasks(task_id: int, data: TaskRelatedSet, service: TaskServiceDep):
    return await service.set_related_tasks(task_id, data.task_ids)
