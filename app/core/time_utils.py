from datetime import UTC, datetime


def to_naive_utc(value: datetime) -> datetime:
    """Приводит datetime к naive UTC — формату, в котором даты хранятся в БД."""
    if value.tzinfo is None:
        return value
    return value.astimezone(UTC).replace(tzinfo=None)
