"""Тесты TaskSuggestionService: валидация ответа ML против данных БД."""

from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import pytest
from sqlalchemy.ext.asyncio import AsyncSession

from app.integrations.openrouter_client import RawTaskSuggestion, SuggestionCandidate
from app.models import Label, Skill
from app.repositories.skill_repo import SkillRepository
from app.repositories.task_repo import TaskRepository
from app.schemas.task_suggestion import TaskAnalyzeRequest
from app.services.exceptions import ServiceUnavailableError
from app.services.task_suggestion_service import TaskSuggestionService


class FakeSuggestionSource:
    def __init__(self, result: RawTaskSuggestion):
        self.result = result
        self.calls: list[dict] = []

    async def suggest_fields(
        self,
        title: str,
        description: str | None,
        labels: list[SuggestionCandidate],
        skills: list[SuggestionCandidate],
    ) -> RawTaskSuggestion:
        self.calls.append(
            {"title": title, "description": description, "labels": labels, "skills": skills}
        )
        return self.result


async def _seed(session: AsyncSession) -> tuple[list[Label], list[Skill]]:
    labels = [Label(name="Backend", color="#3B82F6"), Label(name="Frontend", color="#10B981")]
    skills = [Skill(name="Python"), Skill(name="React")]
    session.add_all([*labels, *skills])
    await session.flush()
    return labels, skills


def _service(session: AsyncSession, source) -> TaskSuggestionService:
    return TaskSuggestionService(
        task_repo=TaskRepository(session),
        skill_repo=SkillRepository(session),
        source=source,
    )


def _request() -> TaskAnalyzeRequest:
    return TaskAnalyzeRequest(title="Fix login bug", description="Users cannot log in")


async def test_analyze_returns_validated_suggestion(db_session):
    labels, skills = await _seed(db_session)
    source = FakeSuggestionSource(
        RawTaskSuggestion(
            difficulty=4,
            label_ids=[labels[0].id],
            skills=[(skills[0].id, 120)],
        )
    )

    result = await _service(db_session, source).analyze(_request())

    assert result.difficulty == 4
    assert [label.id for label in result.labels] == [labels[0].id]
    assert [(item.skill.id, item.exp_reward) for item in result.skills] == [(skills[0].id, 120)]

    call = source.calls[0]
    assert call["title"] == "Fix login bug"
    assert call["description"] == "Users cannot log in"
    assert call["labels"] == [SuggestionCandidate(id=label.id, name=label.name) for label in labels]
    assert call["skills"] == [SuggestionCandidate(id=skill.id, name=skill.name) for skill in skills]


async def test_analyze_filters_unknown_ids(db_session):
    labels, skills = await _seed(db_session)
    source = FakeSuggestionSource(
        RawTaskSuggestion(
            difficulty=3,
            label_ids=[labels[0].id, 999],
            skills=[(skills[1].id, 50), (888, 50)],
        )
    )

    result = await _service(db_session, source).analyze(_request())

    assert [label.id for label in result.labels] == [labels[0].id]
    assert [item.skill.id for item in result.skills] == [skills[1].id]


@pytest.mark.parametrize(
    ("raw_difficulty", "expected"),
    [(0, 1), (-3, 1), (10, 5), (None, 3)],
)
async def test_analyze_clamps_difficulty(db_session, raw_difficulty, expected):
    await _seed(db_session)
    source = FakeSuggestionSource(RawTaskSuggestion(difficulty=raw_difficulty))

    result = await _service(db_session, source).analyze(_request())

    assert result.difficulty == expected


@pytest.mark.parametrize(("raw_reward", "expected"), [(0, 1), (-5, 1), (5000, 1000)])
async def test_analyze_clamps_exp_reward(db_session, raw_reward, expected):
    _, skills = await _seed(db_session)
    source = FakeSuggestionSource(
        RawTaskSuggestion(difficulty=3, skills=[(skills[0].id, raw_reward)])
    )

    result = await _service(db_session, source).analyze(_request())

    assert [item.exp_reward for item in result.skills] == [expected]


async def test_analyze_converts_estimated_days_to_deadline(db_session):
    await _seed(db_session)
    source = FakeSuggestionSource(RawTaskSuggestion(difficulty=3, estimated_days=7))

    before = datetime.now(UTC)
    result = await _service(db_session, source).analyze(_request())
    after = datetime.now(UTC)

    assert result.deadline is not None
    assert before + timedelta(days=7, seconds=-1) <= result.deadline <= after + timedelta(days=7)


async def test_analyze_without_estimated_days_returns_no_deadline(db_session):
    await _seed(db_session)
    source = FakeSuggestionSource(RawTaskSuggestion(difficulty=3, estimated_days=None))

    result = await _service(db_session, source).analyze(_request())

    assert result.deadline is None


@pytest.mark.parametrize(("raw_days", "expected_days"), [(0, 1), (-10, 1), (9000, 365)])
async def test_analyze_clamps_estimated_days(db_session, raw_days, expected_days):
    await _seed(db_session)
    source = FakeSuggestionSource(RawTaskSuggestion(difficulty=3, estimated_days=raw_days))

    result = await _service(db_session, source).analyze(_request())

    assert result.deadline is not None
    delta = result.deadline - datetime.now(UTC)
    assert timedelta(days=expected_days, hours=-1) <= delta <= timedelta(days=expected_days)


async def test_analyze_deduplicates_skills(db_session):
    _, skills = await _seed(db_session)
    source = FakeSuggestionSource(
        RawTaskSuggestion(
            difficulty=3,
            skills=[(skills[0].id, 100), (skills[0].id, 200)],
        )
    )

    result = await _service(db_session, source).analyze(_request())

    assert [(item.skill.id, item.exp_reward) for item in result.skills] == [(skills[0].id, 100)]


async def test_analyze_without_source_raises_service_unavailable(db_session):
    service = _service(db_session, source=None)

    with pytest.raises(ServiceUnavailableError):
        await service.analyze(_request())


async def test_analyze_with_empty_catalogs_returns_empty_lists(db_session):
    source = FakeSuggestionSource(RawTaskSuggestion(difficulty=2, label_ids=[1], skills=[(1, 100)]))

    result = await _service(db_session, source).analyze(_request())

    assert result.difficulty == 2
    assert result.labels == []
    assert result.skills == []
    assert source.calls[0]["labels"] == []
    assert source.calls[0]["skills"] == []


async def test_analyze_loads_all_skill_candidate_pages(db_session):
    class PaginatedSkillRepository:
        def __init__(self) -> None:
            self.calls: list[tuple[int, int]] = []

        async def list(self, limit: int, offset: int):
            self.calls.append((limit, offset))
            if offset == 0:
                return [
                    SimpleNamespace(id=index, name=f"Skill {index}") for index in range(1, 1001)
                ]
            return [SimpleNamespace(id=1001, name="Last skill")] if offset == 1000 else []

    source = FakeSuggestionSource(RawTaskSuggestion(difficulty=3))
    skill_repo = PaginatedSkillRepository()
    service = TaskSuggestionService(
        task_repo=TaskRepository(db_session),
        skill_repo=skill_repo,
        source=source,
    )

    await service.analyze(_request())

    assert skill_repo.calls == [(1000, 0), (1000, 1000)]
    assert source.calls[0]["skills"][-1] == SuggestionCandidate(id=1001, name="Last skill")
