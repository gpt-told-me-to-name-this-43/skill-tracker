from fastapi import APIRouter, status

from app.api.deps import PaginationDep, SkillServiceDep
from app.schemas.skill import SkillCreate, SkillRead, SkillUpdate

router = APIRouter()


@router.get("", response_model=list[SkillRead])
async def list_skills(service: SkillServiceDep, pagination: PaginationDep):
    return await service.list(pagination.limit, pagination.offset)


@router.post("", response_model=SkillRead, status_code=status.HTTP_201_CREATED)
async def create_skill(data: SkillCreate, service: SkillServiceDep):
    return await service.create(data)


@router.get("/{skill_id}", response_model=SkillRead)
async def get_skill(skill_id: int, service: SkillServiceDep):
    return await service.get(skill_id)


@router.patch("/{skill_id}", response_model=SkillRead)
async def update_skill(skill_id: int, data: SkillUpdate, service: SkillServiceDep):
    return await service.update(skill_id, data)


@router.delete("/{skill_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_skill(skill_id: int, service: SkillServiceDep):
    await service.delete(skill_id)
