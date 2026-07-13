from typing import Annotated

from fastapi import Depends, Query
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.integrations.github_client import GitHubClient, GitHubIssueSource
from app.models.user import User
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.repositories.team_repo import TeamRepository
from app.repositories.user_repo import UserRepository
from app.services.auth_service import AuthService
from app.services.exceptions import UnauthorizedError
from app.services.experience import DefaultExperienceAwarder, ExperienceService
from app.services.github_import_service import GitHubImportService
from app.services.skill_service import SkillService
from app.services.task_service import TaskService
from app.services.team_service import TeamService
from app.services.user_service import UserService

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
    experience_repo = ExperienceRepository(db)
    return ExperienceService(
        experience_repo=experience_repo,
        task_repo=TaskRepository(db),
        skill_repo=SkillRepository(db),
        user_repo=UserRepository(db),
        experience_awarder=DefaultExperienceAwarder(experience_repo),
    )


ExperienceServiceDep = Annotated[ExperienceService, Depends(get_experience_service)]


async def get_github_issue_source() -> GitHubIssueSource:
    return GitHubClient(
        repo=settings.github_repo,
        api_url=settings.github_api_url,
        token=settings.github_token,
    )


GitHubIssueSourceDep = Annotated[GitHubIssueSource, Depends(get_github_issue_source)]


async def get_github_import_service(
    db: DbSession,
    source: GitHubIssueSourceDep,
) -> GitHubImportService:
    return GitHubImportService(
        source=source,
        task_repo=TaskRepository(db),
        user_repo=UserRepository(db),
    )


GitHubImportServiceDep = Annotated[GitHubImportService, Depends(get_github_import_service)]


async def get_user_service(db: DbSession) -> UserService:
    return UserService(UserRepository(db))


UserServiceDep = Annotated[UserService, Depends(get_user_service)]


async def get_team_service(db: DbSession) -> TeamService:
    return TeamService(TeamRepository(db))


TeamServiceDep = Annotated[TeamService, Depends(get_team_service)]


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

# Логин отдаёт JWT через JSON POST /api/v1/auth/login; в Swagger Authorize
# вставляется готовый токен, поэтому схема — Bearer, а не OAuth2 password flow.
bearer_scheme = HTTPBearer(auto_error=False)


async def get_auth_service(db: DbSession) -> AuthService:
    return AuthService(UserRepository(db))


AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    auth_service: AuthServiceDep,
) -> User:
    if credentials is None:
        raise UnauthorizedError("Not authenticated")

    return await auth_service.get_user_from_token(credentials.credentials)


CurrentUser = Annotated[User, Depends(get_current_user)]
