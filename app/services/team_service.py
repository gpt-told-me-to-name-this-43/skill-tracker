from collections.abc import Sequence

from app.models.team import Team, TeamMember
from app.repositories.team_repo import TeamRepository
from app.schemas.team import TeamCreate, TeamMembersSet, TeamUpdate
from app.services.exceptions import (
    BadRequestError,
    ConflictError,
    NotFoundError,
    UnprocessableEntityError,
)


class TeamService:
    def __init__(self, team_repo: TeamRepository) -> None:
        self.team_repo = team_repo

    async def list_teams(self) -> Sequence[Team]:
        return await self.team_repo.list_teams()

    async def get_team_by_id(self, team_id: int) -> Team:
        team = await self.team_repo.get_team_by_id(team_id)
        if team is None:
            raise NotFoundError("Team not found")
        return team

    async def _ensure_name_available(self, name: str, current_team_id: int | None = None) -> None:
        existing = await self.team_repo.get_team_by_name(name)
        if existing is not None and existing.id != current_team_id:
            raise ConflictError("Team name already taken")

    async def create_team(self, data: TeamCreate) -> Team:
        await self._ensure_name_available(data.name)
        return await self.team_repo.create_team(data.name, data.description)

    async def update_team(self, team_id: int, data: TeamUpdate) -> Team:
        team = await self.get_team_by_id(team_id)
        fields = data.model_dump(exclude_unset=True)

        if "name" in fields:
            await self._ensure_name_available(fields["name"], current_team_id=team.id)

        if not fields:
            return team

        return await self.team_repo.update_team(team, fields)

    async def set_members(self, team_id: int, data: TeamMembersSet) -> Team:
        team = await self.get_team_by_id(team_id)

        if data.lead_id is not None and data.lead_id not in data.user_ids:
            raise BadRequestError("Lead must be a team member")

        users = await self.team_repo.get_users_by_ids(data.user_ids)
        found_user_ids = {user.id for user in users}
        missing_user_ids = sorted(set(data.user_ids) - found_user_ids)
        if missing_user_ids:
            raise NotFoundError(f"Users not found: {missing_user_ids}")

        desired_user_ids = set(data.user_ids)
        current_memberships = await self.team_repo.get_memberships_for_team(team.id)
        current_by_user_id = {membership.user_id: membership for membership in current_memberships}

        for membership in current_memberships:
            if membership.user_id not in desired_user_ids:
                await self.team_repo.delete_membership(membership)

        incoming_memberships = await self.team_repo.get_memberships_for_users(data.user_ids)
        incoming_by_user_id = {
            membership.user_id: membership for membership in incoming_memberships
        }

        for user_id in data.user_ids:
            membership = incoming_by_user_id.get(user_id)
            if membership is not None and membership.team_id == team.id:
                continue

            if membership is not None:
                await self._move_user_from_previous_team(membership)

            if user_id not in current_by_user_id or (
                membership is not None and membership.team_id != team.id
            ):
                await self.team_repo.add_membership(team.id, user_id)

        await self.team_repo.set_team_lead(team, data.lead_id)
        refreshed_team = await self.team_repo.get_team_by_id(team.id)
        if refreshed_team is None:
            raise UnprocessableEntityError("Team update failed")
        return refreshed_team

    async def _move_user_from_previous_team(self, membership: TeamMember) -> None:
        previous_team = membership.team
        if previous_team.lead_id == membership.user_id:
            await self.team_repo.set_team_lead(previous_team, None)
        await self.team_repo.delete_membership(membership)
