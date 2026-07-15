from fastapi import APIRouter, status

from app.api.deps import CurrentUser, TeamServiceDep
from app.schemas.team import TeamCreate, TeamMembersSet, TeamRead, TeamUpdate

router = APIRouter()


@router.get("/teams", response_model=list[TeamRead])
async def list_teams(
    service: TeamServiceDep,
    current_user: CurrentUser,
) -> list[TeamRead]:
    return await service.list_teams()


@router.get("/teams/{team_id}", response_model=TeamRead)
async def get_team(
    team_id: int,
    service: TeamServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    return await service.get_team_by_id(team_id)


@router.post("/teams", response_model=TeamRead, status_code=status.HTTP_201_CREATED)
async def create_team(
    data: TeamCreate,
    service: TeamServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    # TODO(epic:auth-rbac): restrict team creation.
    return await service.create_team(data)


@router.patch("/teams/{team_id}", response_model=TeamRead)
async def update_team(
    team_id: int,
    data: TeamUpdate,
    service: TeamServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    # TODO(epic:auth-rbac): restrict team updates.
    return await service.update_team(team_id, data)


@router.put("/teams/{team_id}/members", response_model=TeamRead)
async def set_team_members(
    team_id: int,
    data: TeamMembersSet,
    service: TeamServiceDep,
    current_user: CurrentUser,
) -> TeamRead:
    # TODO(epic:auth-rbac): restrict team membership updates.
    return await service.set_members(team_id, data)
