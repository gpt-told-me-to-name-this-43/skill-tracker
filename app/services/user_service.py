from collections.abc import Sequence

from app.models.user import User
from app.repositories.user_repo import UserRepository
from app.services.exceptions import NotFoundError


class UserService:
    def __init__(self, user_repo: UserRepository) -> None:
        self.user_repo = user_repo

    async def list_users(self, limit: int, offset: int) -> Sequence[User]:
        return await self.user_repo.list_users(limit, offset)

    async def get_user_by_id(self, user_id: int) -> User:
        user = await self.user_repo.get_user_by_id(user_id)
        if user is None:
            raise NotFoundError("User not found")
        return user
