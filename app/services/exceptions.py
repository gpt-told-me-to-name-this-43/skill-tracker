class DomainError(Exception):
    """База для ошибок бизнес-логики."""


class NotFoundError(DomainError):
    pass


class ConflictError(DomainError):
    pass


class PermissionDeniedError(DomainError):
    pass
