import jwt

from app.core.security import (
    create_access_token,
    decode_access_token,
    hash_password,
    verify_password,
)
from app.models.user import User
from app.repositories.user_repo import UserRepository
from app.services.exceptions import ConflictError, UnauthorizedError


class AuthService:
    def __init__(self, user_repo: UserRepository) -> None:
        self.user_repo = user_repo

    def _normalize_email(self, email: str) -> str:
        return email.strip().lower()

    async def register(self, email: str, username: str, password: str) -> User:
        email = self._normalize_email(email)

        if await self.user_repo.get_by_email(email):
            raise ConflictError("Email already registered")
        if await self.user_repo.get_by_username(username):
            raise ConflictError("Username already taken")

        hashed_password = hash_password(password)
        user = await self.user_repo.create(email, username, hashed_password)
        return user

    async def login(self, email: str, password: str) -> str:
        email = self._normalize_email(email)
        user = await self.user_repo.get_by_email(email)

        if not user or not verify_password(password, user.hashed_password):
            raise UnauthorizedError("Invalid credentials")

        return create_access_token(user.id)

    async def get_user_from_token(self, token: str) -> User:
        try:
            payload = decode_access_token(token)
            user_id = int(payload.get("sub"))
        except (jwt.PyJWTError, ValueError, TypeError):
            raise UnauthorizedError("Invalid token")

        user = await self.user_repo.get(user_id)
        if not user:
            raise UnauthorizedError("Invalid token")
        return user