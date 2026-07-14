from fastapi import APIRouter

from app.api.deps import CurrentUser, PaginationDep, UserServiceDep
from app.models.enums import MemberStatus
from app.schemas.user import UserPublicRead, UserWorkspaceProfileUpdate

router = APIRouter()


@router.get("/users", response_model=list[UserPublicRead])
async def list_users(
    pagination: PaginationDep,
    service: UserServiceDep,
    current_user: CurrentUser,
    team_id: int | None = None,
    member_status: MemberStatus | None = None,
) -> list[UserPublicRead]:
    return await service.list_users(pagination.limit, pagination.offset, team_id, member_status)


@router.get("/users/{user_id}", response_model=UserPublicRead)
async def get_user(
    user_id: int,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> UserPublicRead:
    return await service.get_user_by_id(user_id)


@router.patch("/users/{user_id}/workspace-profile", response_model=UserPublicRead)
async def update_workspace_profile(
    user_id: int,
    data: UserWorkspaceProfileUpdate,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> UserPublicRead:
    # TODO(epic:auth-rbac): restrict workspace profile updates.
    return await service.update_workspace_profile(user_id, data)
