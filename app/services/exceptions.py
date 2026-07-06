class DomainError(Exception):
    """База для ошибок бизнес-логики."""


class NotFoundError(DomainError):
    __slots__ = ()


class ConflictError(DomainError):
    __slots__ = ()


class PermissionDeniedError(DomainError):
    __slots__ = ()
