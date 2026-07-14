"""ML-подсказки полей задачи: labels, skills, difficulty и deadline по title/description."""

from datetime import UTC, datetime, timedelta

from app.integrations.openrouter_client import SuggestionCandidate, TaskSuggestionSource
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.schemas.experience import TaskSkillRead
from app.schemas.task_suggestion import (
    TaskAnalyzeRequest,
    TaskFieldSuggestion,
)
from app.services.exceptions import ServiceUnavailableError

_DEFAULT_DIFFICULTY = 3
_SKILL_CANDIDATE_PAGE_SIZE = 1000


def _clamp(value: int, low: int, high: int) -> int:
    return max(low, min(high, value))


class TaskSuggestionService:
    """Запрашивает подсказки у ML-провайдера и валидирует их против данных БД.

    Ответу модели не доверяем: несуществующие id отбрасываются,
    difficulty и exp_reward зажимаются в допустимые диапазоны.
    """

    def __init__(
        self,
        task_repo: TaskRepository,
        skill_repo: SkillRepository,
        source: TaskSuggestionSource | None,
    ) -> None:
        self.task_repo = task_repo
        self.skill_repo = skill_repo
        self.source = source

    async def analyze(self, data: TaskAnalyzeRequest) -> TaskFieldSuggestion:
        if self.source is None:
            raise ServiceUnavailableError("ML suggestions are unavailable: set OPENROUTER_API_KEY")

        labels = await self.task_repo.list_labels()
        skills = []
        offset = 0
        while True:
            page = await self.skill_repo.list(limit=_SKILL_CANDIDATE_PAGE_SIZE, offset=offset)
            skills.extend(page)
            if len(page) < _SKILL_CANDIDATE_PAGE_SIZE:
                break
            offset += len(page)

        raw = await self.source.suggest_fields(
            title=data.title,
            description=data.description,
            labels=[SuggestionCandidate(id=label.id, name=label.name) for label in labels],
            skills=[SuggestionCandidate(id=skill.id, name=skill.name) for skill in skills],
        )

        labels_by_id = {label.id: label for label in labels}
        skills_by_id = {skill.id: skill for skill in skills}

        difficulty = _DEFAULT_DIFFICULTY
        if raw.difficulty is not None:
            difficulty = _clamp(raw.difficulty, 1, 5)

        # Модель оценивает срок в днях; в конкретную дату переводим здесь,
        # потому что LLM не знает текущей даты.
        deadline = None
        if raw.estimated_days is not None:
            days = _clamp(raw.estimated_days, 1, 365)
            deadline = datetime.now(UTC).replace(microsecond=0) + timedelta(days=days)

        suggested_labels = [
            labels_by_id[label_id] for label_id in raw.label_ids if label_id in labels_by_id
        ]

        seen_skill_ids: set[int] = set()
        suggested_skills: list[TaskSkillRead] = []
        for skill_id, exp_reward in raw.skills:
            skill = skills_by_id.get(skill_id)
            if skill is None or skill_id in seen_skill_ids:
                continue
            seen_skill_ids.add(skill_id)
            suggested_skills.append(
                TaskSkillRead(
                    skill=skill,
                    exp_reward=_clamp(exp_reward, 1, 1000),
                )
            )

        return TaskFieldSuggestion(
            difficulty=difficulty,
            deadline=deadline,
            labels=suggested_labels,
            skills=suggested_skills,
        )
