from typing import Annotated

from fastapi import Depends, HTTPException, Query, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.repositories.skill_repo import SkillRepository
from app.services.skill_service import SkillService

# tokenUrl укажет на реальный эндпоинт логина из Auth
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")

DbSession = Annotated[AsyncSession, Depends(get_db)]


def get_skill_service(db: DbSession) -> SkillService:
    return SkillService(SkillRepository(db))


SkillServiceDep = Annotated[SkillService, Depends(get_skill_service)]


class Pagination:
    def __init__(
        self,
        limit: int = Query(20, ge=1, le=100),
        offset: int = Query(0, ge=0),
    ) -> None:
        self.limit = limit
        self.offset = offset


PaginationDep = Annotated[Pagination, Depends(Pagination)]


async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    db: DbSession,
):
    """ЗАГЛУШКА!
    Сейчас валидирует токен и возвращает payload, чтобы защищённые
    роуты можно было размечать с самого начала.
    """
    from app.core.security import decode_access_token

    try:
        payload = decode_access_token(token)
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from None
    return payload


CurrentUser = Annotated[dict, Depends(get_current_user)]
