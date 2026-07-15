class DomainError(Exception):
    """База для ошибок бизнес-логики."""


class NotFoundError(DomainError):
    __slots__ = ()


class BadRequestError(DomainError):
    pass


class UnprocessableEntityError(DomainError):
    pass


class ConflictError(DomainError):
    __slots__ = ()


class PermissionDeniedError(DomainError):
    __slots__ = ()


class UnauthorizedError(DomainError):
    __slots__ = ()


class ServiceUnavailableError(DomainError):
    """Внешняя зависимость временно недоступна."""

    __slots__ = ()
