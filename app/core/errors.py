from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.services.exceptions import (
    BadRequestError,
    ConflictError,
    NotFoundError,
    PermissionDeniedError,
    UnauthorizedError,
    UnprocessableEntityError,
)


def _error_body(message: str, details: object = None) -> dict:
    return {"error": {"message": message, "details": details}}


def _json_safe(value: object) -> object:
    if isinstance(value, BaseException):
        return str(value)
    if isinstance(value, dict):
        return {key: _json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_json_safe(item) for item in value]
    if isinstance(value, tuple):
        return tuple(_json_safe(item) for item in value)
    return value


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(StarletteHTTPException)
    async def http_exception_handler(_: Request, exc: StarletteHTTPException):
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_body(str(exc.detail)),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(_: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content=_error_body("Validation error", _json_safe(exc.errors())),
        )

    @app.exception_handler(NotFoundError)
    async def not_found_handler(_: Request, exc: NotFoundError):
        return JSONResponse(status_code=404, content=_error_body(str(exc)))

    @app.exception_handler(ConflictError)
    async def conflict_handler(_: Request, exc: ConflictError):
        return JSONResponse(status_code=409, content=_error_body(str(exc)))

    @app.exception_handler(PermissionDeniedError)
    async def permission_handler(_: Request, exc: PermissionDeniedError):
        return JSONResponse(status_code=403, content=_error_body(str(exc)))

    @app.exception_handler(BadRequestError)
    async def bad_request_handler(_: Request, exc: BadRequestError):
        return JSONResponse(status_code=400, content=_error_body(str(exc)))

    @app.exception_handler(UnprocessableEntityError)
    async def unprocessable_entity_handler(_: Request, exc: UnprocessableEntityError):
        return JSONResponse(status_code=422, content=_error_body(str(exc)))

    @app.exception_handler(UnauthorizedError)
    async def unauthorized_handler(_: Request, exc: UnauthorizedError):
        return JSONResponse(
            status_code=401,
            content=_error_body(str(exc)),
            headers={"WWW-Authenticate": "Bearer"},
        )
