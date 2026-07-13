from fastapi import APIRouter

from app.api.deps import CurrentUser, GitHubImportServiceDep
from app.schemas.github_import import GitHubSyncResult

router = APIRouter()


@router.post("/integrations/github/sync", response_model=GitHubSyncResult)
async def sync_github_issues(
    service: GitHubImportServiceDep,
    current_user: CurrentUser,
):
    """Импортировать issues из настроенного GitHub-репозитория (односторонне, идемпотентно)."""
    return await service.sync(current_user_id=current_user.id)
