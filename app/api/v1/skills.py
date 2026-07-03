from fastapi import APIRouter, status

from app.api.deps import PaginationDep, SkillServiceDep
from app.schemas.skill import SkillCreate, SkillRead, SkillUpdate, UserSkillAssign, UserSkillRead, UserProgressRead

router = APIRouter()


@router.get("/skills", response_model=list[SkillRead])
async def list_skills(service: SkillServiceDep, pagination: PaginationDep):
    return await service.list(pagination.limit, pagination.offset)


@router.post("/skills", response_model=SkillRead, status_code=status.HTTP_201_CREATED)
async def create_skill(data: SkillCreate, service: SkillServiceDep):
    # TODO(epic:auth): admin-only, return 403 for non-admin users
    return await service.create(data)


@router.get("/skills/{skill_id}", response_model=SkillRead)
async def get_skill(skill_id: int, service: SkillServiceDep):
    return await service.get(skill_id)


@router.patch("/skills/{skill_id}", response_model=SkillRead)
async def update_skill(skill_id: int, data: SkillUpdate, service: SkillServiceDep):
    return await service.update(skill_id, data)


@router.delete("/skills/{skill_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_skill(skill_id: int, service: SkillServiceDep):
    await service.delete(skill_id)


# Эндпоинты для пользователей
@router.post("/users/{user_id}/skills", response_model=UserSkillRead, status_code=status.HTTP_201_CREATED)
async def assign_skill_to_user(
    user_id: int,
    data: UserSkillAssign,
    service: SkillServiceDep,
):
    return await service.assign_skill_to_user(user_id, data.skill_id)


@router.get("/users/{user_id}/skills", response_model=list[UserSkillRead])
async def get_user_skills(user_id: int, service: SkillServiceDep):
    return await service.get_user_skills(user_id)


@router.get("/users/{user_id}/progress", response_model=UserProgressRead)
async def get_user_progress(user_id: int, service: SkillServiceDep):
    return await service.get_user_progress(user_id)
