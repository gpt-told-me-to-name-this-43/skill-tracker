from fastapi import FastAPI

from app.api.v1 import skills
from app.core.config import settings
from app.core.errors import register_exception_handlers


def create_app() -> FastAPI:
    app = FastAPI(title=settings.app_name, debug=settings.debug)

    register_exception_handlers(app)

    @app.get("/health", tags=["system"])
    async def health() -> dict[str, str]:
        return {"status": "ok"}

    # app.include_router(auth.router, prefix="/api/v1/auth", tags=["auth"])
    # app.include_router(users.router, prefix="/api/v1/users", tags=["users"])
    app.include_router(skills.router, prefix="/api/v1/skills", tags=["skills"])
    # app.include_router(tasks.router, prefix="/api/v1/tasks", tags=["tasks"])

    return app


app = create_app()
