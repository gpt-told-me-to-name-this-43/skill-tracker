from typing import Protocol

from app.models.task import Task


class ExperienceAwarder(Protocol):
    """
    Контракт (интерфейс) для начисления опыта за задачу.
    Tasks-блок НЕ знает про реализацию Experience-блока.
    Он знает только этот контракт!!!!!
    Когда Experience-блок будет готов, он предоставит свою реализацию
    этого протокола, и Tasks автоматически начнёт начислять опыт.
    """

    async def award_for_task(self, task: Task) -> None:
        """
        Начислить опыт за выполнение задачи.
        """
        ...


class NoOpAwarder:
    """
    Заглушка-реализация ExperienceAwarder.
    Используется ПОКА Experience-блок не готов.
    Ничего не делает, просто возвращает None.
    """

    async def award_for_task(self, task: Task) -> None:
        """Ничего не делаем — заглушка."""
        return None
