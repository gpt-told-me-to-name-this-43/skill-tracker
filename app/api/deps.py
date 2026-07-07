from typing import Annotated

from fastapi import Depends, HTTPException, Query, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.user import User
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.services.experience import NoOpAwarder
from app.services.skill_service import SkillService
from app.services.task_service import TaskService

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")

DbSession = Annotated[AsyncSession, Depends(get_db)]


async def get_skill_service(db: DbSession) -> SkillService:
    return SkillService(SkillRepository(db))


SkillServiceDep = Annotated[SkillService, Depends(get_skill_service)]


async def get_task_service(db: DbSession) -> TaskService:
    """
    Dependency для TaskService.

    Инжектит NoOpAwarder как заглушку для Experience.

    TODO(epic:experience): заменить на реальный ExperienceAwarder
    """
    task_repo = TaskRepository(db)
    user_repo = UserRepository(db)

    # TODO(epic:experience): replace NoOpAwarder with real ExperienceAwarder
    awarder = NoOpAwarder()

    return TaskService(
        task_repo=task_repo,
        user_repo=user_repo,
        experience_awarder=awarder,
    )


TaskServiceDep = Annotated[TaskService, Depends(get_task_service)]


class Pagination:
    def __init__(
        self,
        limit: int = Query(20, ge=1, le=100),
        offset: int = Query(0, ge=0),
    ) -> None:
        self.limit = limit
        self.offset = offset


async def get_pagination(
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
) -> Pagination:
    return Pagination(limit, offset)


PaginationDep = Annotated[Pagination, Depends(get_pagination)]


def unauthorized_error() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired token",
        headers={"WWW-Authenticate": "Bearer"},
    )


async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    db: DbSession,
) -> User:
    from app.core.security import decode_access_token

    try:
        payload = decode_access_token(token)
        user_id = int(payload["sub"])
    except Exception:
        raise unauthorized_error() from None

    user = await db.scalar(select(User).where(User.id == user_id))

    if user is None:
        raise unauthorized_error()

    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
