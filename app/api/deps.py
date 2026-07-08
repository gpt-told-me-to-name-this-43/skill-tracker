from typing import Annotated

from fastapi import Depends, Query
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.user import User
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.services.auth_service import AuthService
from app.services.experience import DefaultExperienceAwarder, ExperienceService
from app.services.skill_service import SkillService
from app.services.task_service import TaskService

DbSession = Annotated[AsyncSession, Depends(get_db)]


async def get_skill_service(db: DbSession) -> SkillService:
    return SkillService(SkillRepository(db))


SkillServiceDep = Annotated[SkillService, Depends(get_skill_service)]


async def get_task_service(db: DbSession) -> TaskService:
    """
    Dependency для TaskService.

    Инжектит ExperienceAwarder для начисления XP при переходе задачи в done.
    """
    task_repo = TaskRepository(db)
    user_repo = UserRepository(db)

    awarder = DefaultExperienceAwarder(ExperienceRepository(db))

    return TaskService(
        task_repo=task_repo,
        user_repo=user_repo,
        experience_awarder=awarder,
    )


TaskServiceDep = Annotated[TaskService, Depends(get_task_service)]


async def get_experience_service(db: DbSession) -> ExperienceService:
    return ExperienceService(
        experience_repo=ExperienceRepository(db),
        task_repo=TaskRepository(db),
        skill_repo=SkillRepository(db),
        user_repo=UserRepository(db),
    )


ExperienceServiceDep = Annotated[ExperienceService, Depends(get_experience_service)]


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

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")


async def get_auth_service(db: DbSession) -> AuthService:
    return AuthService(UserRepository(db))


AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]


async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    auth_service: AuthServiceDep,
) -> User:
    # Если токен битый/истёк, auth_service выбросит UnauthorizedError,
    # который перехватится глобальным обработчиком
    return await auth_service.get_user_from_token(token)


CurrentUser = Annotated[User, Depends(get_current_user)]
