from fastapi import APIRouter, status

from app.api.deps import CurrentUser, PaginationDep, UserServiceDep
from app.schemas.team import TeamCreate, TeamMembersSet, TeamRead, TeamUpdate
from app.schemas.user import MemberStatus, PersonRead, WorkspaceProfileUpdate

router = APIRouter()


@router.get("/users", response_model=list[PersonRead])
async def list_users(
    pagination: PaginationDep,
    service: UserServiceDep,
    current_user: CurrentUser,
    team_id: int | None = None,
    member_status: MemberStatus | None = None,
) -> list[PersonRead]:
    return await service.list_users(
        pagination.limit,
        pagination.offset,
        team_id,
        member_status,
    )


@router.get("/users/{user_id}", response_model=PersonRead)
async def get_user(
    user_id: int,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> PersonRead:
    return await service.get_person_by_id(user_id)


@router.patch("/users/{user_id}/workspace-profile", response_model=PersonRead)
async def update_workspace_profile(
    user_id: int,
    data: WorkspaceProfileUpdate,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> PersonRead:
    return await service.update_workspace_profile(user_id, data, current_user)


@router.get("/teams", response_model=list[TeamRead])
async def list_teams(
    service: UserServiceDep,
    current_user: CurrentUser,
) -> list[TeamRead]:
    return await service.list_teams()


@router.get("/teams/{team_id}", response_model=TeamRead)
async def get_team(
    team_id: int,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    return await service.get_team_by_id(team_id)


@router.post("/teams", response_model=TeamRead, status_code=status.HTTP_201_CREATED)
async def create_team(
    data: TeamCreate,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    return await service.create_team(data, current_user)


@router.patch("/teams/{team_id}", response_model=TeamRead)
async def update_team(
    team_id: int,
    data: TeamUpdate,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    return await service.update_team(team_id, data, current_user)


@router.put("/teams/{team_id}/members", response_model=TeamRead)
async def set_team_members(
    team_id: int,
    data: TeamMembersSet,
    service: UserServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    return await service.set_team_members(team_id, data, current_user)
