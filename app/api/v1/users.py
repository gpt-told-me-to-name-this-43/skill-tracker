from fastapi import APIRouter

from app.api.deps import CurrentUser, PaginationDep, UserServiceDep
from app.schemas.auth import UserRead

router = APIRouter()


@router.get("/users", response_model=list[UserRead])
async def list_users(
    pagination: PaginationDep,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> list[UserRead]:
    return await service.list_users(pagination.limit, pagination.offset)


@router.get("/users/{user_id}", response_model=UserRead)
async def get_user(
    user_id: int,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> UserRead:
    return await service.get_user_by_id(user_id)
