from collections.abc import Sequence

from app.models.enums import MemberStatus
from app.models.user import User
from app.repositories.user_repo import UserRepository
from app.schemas.user import UserWorkspaceProfileUpdate
from app.services.exceptions import NotFoundError


class UserService:
    def __init__(self, user_repo: UserRepository) -> None:
        self.user_repo = user_repo

    async def list_users(
        self,
        limit: int,
        offset: int,
        team_id: int | None = None,
        member_status: MemberStatus | None = None,
    ) -> Sequence[User]:
        status_value = member_status.value if member_status is not None else None
        return await self.user_repo.list_users(limit, offset, team_id, status_value)

    async def get_user_by_id(self, user_id: int) -> User:
        user = await self.user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError("User not found")
        return user

    async def update_workspace_profile(
        self,
        user_id: int,
        data: UserWorkspaceProfileUpdate,
    ) -> User:
        user = await self.get_user_by_id(user_id)
        fields = data.model_dump(exclude_unset=True)
        if "member_status" in fields and fields["member_status"] is not None:
            fields["member_status"] = fields["member_status"].value

        return await self.user_repo.update_workspace_profile(user, fields)
