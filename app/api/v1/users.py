from fastapi import APIRouter

from app.api.deps import CurrentUser, DbSession, PaginationDep
from app.repositories.user_repo import UserRepository
from app.schemas.auth import UserRead
from app.services.exceptions import NotFoundError

router = APIRouter()


@router.get("/users", response_model=list[UserRead])
async def list_users(
    pagination: PaginationDep,
    db: DbSession,
    current_user: CurrentUser,
) -> list[UserRead]:
    user_repo = UserRepository(db)
    users = await user_repo.list_users(pagination.limit, pagination.offset)
    return users


@router.get("/users/{user_id}", response_model=UserRead)
async def get_user(
    user_id: int,
    db: DbSession,
    current_user: CurrentUser,
) -> UserRead:
    user_repo = UserRepository(db)
    user = await user_repo.get(user_id)
    if not user:
        raise NotFoundError("User not found")
    return user
