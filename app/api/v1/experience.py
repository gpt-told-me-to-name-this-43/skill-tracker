from fastapi import APIRouter, Query

from app.api.deps import ExperienceServiceDep
from app.schemas.experience import ExperienceLogRead

router = APIRouter()


@router.get("/users/{user_id}/experience-log", response_model=list[ExperienceLogRead])
async def get_user_experience_log(
    user_id: int,
    service: ExperienceServiceDep,
    limit: int = Query(50, ge=1, le=1000),
    offset: int = Query(0, ge=0),
):
    return await service.get_user_log(user_id, limit, offset)
