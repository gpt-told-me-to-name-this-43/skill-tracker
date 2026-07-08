from fastapi import APIRouter, status

from app.api.deps import AuthServiceDep, CurrentUser
from app.schemas.auth import TokenResponse, UserLogin, UserRead, UserRegister

router = APIRouter()


@router.post("/auth/register", response_model=UserRead, status_code=status.HTTP_201_CREATED)
async def register(data: UserRegister, auth_service: AuthServiceDep) -> UserRead:
    user = await auth_service.register(data.email, data.username, data.password)
    return user


@router.post("/auth/login", response_model=TokenResponse)
async def login(data: UserLogin, auth_service: AuthServiceDep) -> TokenResponse:
    token = await auth_service.login(data.email, data.password)
    return TokenResponse(access_token=token, token_type="bearer")


@router.get("/auth/me", response_model=UserRead)
async def get_me(current_user: CurrentUser) -> UserRead:
    return current_user
