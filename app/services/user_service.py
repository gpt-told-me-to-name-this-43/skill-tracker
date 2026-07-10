from collections.abc import Sequence

from app.models.user import Team, User
from app.repositories.user_repo import UserRepository
from app.schemas.team import TeamCreate, TeamMembersSet, TeamUpdate
from app.schemas.user import WorkspaceProfileUpdate
from app.services.exceptions import BadRequestError, ConflictError, NotFoundError, PermissionDeniedError


def serialize_team_summary(team: Team | None) -> dict | None:
    if team is None:
        return None
    return {"id": team.id, "name": team.name}


def serialize_user_summary(user: User) -> dict:
    return {
        "id": user.id,
        "username": user.username,
        "avatar_url": user.avatar_url,
        "position": user.position,
        "member_status": user.member_status,
    }


def serialize_person(user: User) -> dict:
    return {
        **serialize_user_summary(user),
        "role": user.role,
        "team": serialize_team_summary(user.team_membership.team)
        if user.team_membership
        else None,
    }


def serialize_team(team: Team) -> dict:
    members = [member.user for member in team.members]
    return {
        "id": team.id,
        "name": team.name,
        "description": team.description,
        "member_count": len(members),
        "lead": serialize_user_summary(team.lead) if team.lead else None,
        "members": [serialize_user_summary(user) for user in members],
        "created_at": team.created_at,
        "updated_at": team.updated_at,
    }


class UserService:
    def __init__(self, user_repo: UserRepository) -> None:
        self.user_repo = user_repo

    async def list_users(
        self,
        limit: int,
        offset: int,
        team_id: int | None = None,
        member_status: str | None = None,
    ) -> Sequence[dict]:
        users = await self.user_repo.list_users(limit, offset, team_id, member_status)
        return [serialize_person(user) for user in users]

    async def get_user_by_id(self, user_id: int) -> User:
        user = await self.user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError("User not found")
        return user

    async def get_person_by_id(self, user_id: int) -> dict:
        return serialize_person(await self.get_user_by_id(user_id))

    async def update_workspace_profile(
        self,
        user_id: int,
        data: WorkspaceProfileUpdate,
        actor: User,
    ) -> dict:
        self._ensure_workspace_manager(actor)
        user = await self.get_user_by_id(user_id)
        fields = data.model_dump(exclude_unset=True)
        if "avatar_url" in fields and fields["avatar_url"] is not None:
            fields["avatar_url"] = str(fields["avatar_url"])
        if "member_status" in fields and fields["member_status"] is not None:
            fields["member_status"] = fields["member_status"].value
        updated_user = await self.user_repo.update_workspace_profile(user, fields)
        return serialize_person(updated_user)

    async def list_teams(self) -> Sequence[dict]:
        teams = await self.user_repo.list_teams()
        return [serialize_team(team) for team in teams]

    async def get_team_by_id(self, team_id: int) -> dict:
        team = await self.user_repo.get_team_by_id(team_id)
        if team is None:
            raise NotFoundError("Team not found")
        return serialize_team(team)

    async def create_team(self, data: TeamCreate, actor: User) -> dict:
        self._ensure_workspace_manager(actor)
        existing = await self.user_repo.get_team_by_name_ci(data.name)
        if existing is not None:
            raise ConflictError("Team name already exists")
        team = await self.user_repo.create_team(data.name, data.description)
        return serialize_team(await self._get_team_model(team.id))

    async def update_team(self, team_id: int, data: TeamUpdate, actor: User) -> dict:
        self._ensure_workspace_manager(actor)
        team = await self._get_team_model(team_id)
        fields = data.model_dump(exclude_unset=True)
        if "name" in fields:
            existing = await self.user_repo.get_team_by_name_ci(fields["name"])
            if existing is not None and existing.id != team.id:
                raise ConflictError("Team name already exists")
        updated_team = await self.user_repo.update_team(team, fields)
        return serialize_team(await self._get_team_model(updated_team.id))

    async def set_team_members(self, team_id: int, data: TeamMembersSet, actor: User) -> dict:
        self._ensure_workspace_manager(actor)
        team = await self._get_team_model(team_id)
        users = [await self.get_user_by_id(user_id) for user_id in data.user_ids]

        if data.lead_id is not None and data.lead_id not in data.user_ids:
            raise BadRequestError("Lead must be a team member")

        if data.lead_id is not None and all(user.id != data.lead_id for user in users):
            raise NotFoundError("Lead user not found")

        updated_team = await self.user_repo.replace_team_members(
            team,
            data.user_ids,
            data.lead_id,
        )
        return serialize_team(updated_team)

    async def _get_team_model(self, team_id: int) -> Team:
        team = await self.user_repo.get_team_by_id(team_id)
        if team is None:
            raise NotFoundError("Team not found")
        return team

    def _ensure_workspace_manager(self, actor: User) -> None:
        if actor.role != "admin":
            raise PermissionDeniedError("Only workspace admins can manage project people and teams")
