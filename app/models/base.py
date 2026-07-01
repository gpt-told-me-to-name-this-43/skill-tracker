from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    """Общий declarative base. Все модели наследуются от него."""

    pass
