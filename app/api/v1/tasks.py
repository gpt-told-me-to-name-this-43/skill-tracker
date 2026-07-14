from fastapi import APIRouter, Query, Request, status

from app.api.deps import (
    CurrentUser,
    ExperienceServiceDep,
    TaskLintServiceDep,
    TaskServiceDep,
    TaskSuggestionServiceDep,
)
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
    TaskLintReport,
    TaskListItem,
    TaskRelatedSet,
    TaskStatusUpdate,
    TaskUpdate,
)
from app.schemas.task_suggestion import TaskAnalyzeRequest, TaskFieldSuggestion
from app.services.exceptions import BadRequestError

router = APIRouter()


def _parse_multipart_file(content_type: str | None, body: bytes) -> tuple[str, bytes]:
    if not content_type or "multipart/form-data" not in content_type:
        raise BadRequestError("Expected multipart form data")

    boundary_marker = "boundary="
    if boundary_marker not in content_type:
        raise BadRequestError("Multipart boundary is missing")

    boundary = content_type.split(boundary_marker, 1)[1].split(";", 1)[0].strip().strip('"')
    if not boundary:
        raise BadRequestError("Multipart boundary is empty")

    boundary_bytes = f"--{boundary}".encode()
    for part in body.split(boundary_bytes):
        if b'form-data; name="file"' not in part:
            continue

        header_end = part.find(b"\r\n\r\n")
        if header_end == -1:
            raise BadRequestError("Invalid multipart file payload")

        headers = part[:header_end].decode("utf-8", errors="ignore")
        filename = "attachment"
        filename_marker = 'filename="'
        if filename_marker in headers:
            filename = headers.split(filename_marker, 1)[1].split('"', 1)[0] or filename

        content = part[header_end + 4 :]
        content = content.removesuffix(b"\r\n")
        content = content.removesuffix(b"--")
        content = content.removesuffix(b"\r\n")
        return filename, content

    raise BadRequestError("File field is missing")


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


# Объявлен до маршрутов /tasks/{task_id}: иначе "analyze" уйдёт в int-параметр.
@router.post("/tasks/analyze", response_model=TaskFieldSuggestion)
async def analyze_task(
    data: TaskAnalyzeRequest,
    service: TaskSuggestionServiceDep,
    current_user: CurrentUser,
):
    """Предложить labels, skills и difficulty для задачи с помощью ML."""
    return await service.analyze(data)


@router.get("/tasks/{task_id}", response_model=TaskDetail)
async def get_task(
    task_id: int,
    service: TaskServiceDep,
):
    """Получить задачу по ID."""
    return await service.get_task_by_id(task_id)


@router.get("/tasks/{task_id}/lint", response_model=TaskLintReport)
async def lint_task(
    task_id: int,
    service: TaskLintServiceDep,
):
    """Проверить качество задачи и вернуть список предупреждений."""
    return await service.lint_task(task_id)


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


@router.post(
    "/tasks/{task_id}/attachments/upload",
    response_model=TaskAttachmentRead,
    status_code=status.HTTP_201_CREATED,
)
async def upload_attachment(
    task_id: int,
    service: TaskServiceDep,
    current_user: CurrentUser,
    request: Request,
):
    filename, content = _parse_multipart_file(
        request.headers.get("content-type"),
        await request.body(),
    )
    return await service.create_uploaded_attachment(
        task_id,
        filename,
        content,
        current_user.id,
    )


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
