import pytest

from app.models.skill import Skill
from app.schemas.skill import SkillCreate, SkillUpdate
from app.services.exceptions import ConflictError, NotFoundError
from app.services.skill_service import SkillService


class FakeSession:
    async def flush(self):
        pass


class FakeSkillRepo:
    def __init__(self):
        self._items: dict[int, Skill] = {}
        self._counter = 0
        self.session = FakeSession()

    async def get(self, skill_id):
        return self._items.get(skill_id)

    async def get_by_name(self, name):
        # Мимикрирует регистронезависимый поиск реального репозитория.
        target = name.strip().lower()
        return next((s for s in self._items.values() if s.name.lower() == target), None)

    async def create(self, skill):
        self._counter += 1
        skill.id = self._counter
        self._items[skill.id] = skill
        return skill


async def test_create_ok():
    service = SkillService(FakeSkillRepo())
    skill = await service.create(SkillCreate(name="Python"))
    assert skill.id == 1
    assert skill.name == "Python"


async def test_create_duplicate_raises_conflict():
    service = SkillService(FakeSkillRepo())
    await service.create(SkillCreate(name="Python"))
    with pytest.raises(ConflictError):
        await service.create(SkillCreate(name="Python"))


async def test_get_missing_raises_not_found():
    service = SkillService(FakeSkillRepo())
    with pytest.raises(NotFoundError):
        await service.get(999)


async def test_update_explicit_null_clears_description():
    service = SkillService(FakeSkillRepo())
    skill = await service.create(SkillCreate(name="Python", description="Backend"))

    updated = await service.update(skill.id, SkillUpdate(description=None))
    assert updated.description is None


async def test_update_without_description_keeps_it():
    service = SkillService(FakeSkillRepo())
    skill = await service.create(SkillCreate(name="Python", description="Backend"))

    updated = await service.update(skill.id, SkillUpdate(name="Python 3"))
    assert updated.name == "Python 3"
    assert updated.description == "Backend"


async def test_update_whitespace_name_raises_conflict():
    service = SkillService(FakeSkillRepo())
    skill = await service.create(SkillCreate(name="Python"))

    with pytest.raises(ConflictError):
        await service.update(skill.id, SkillUpdate(name="   "))
    assert skill.name == "Python"


async def test_update_duplicate_name_raises_conflict():
    service = SkillService(FakeSkillRepo())
    await service.create(SkillCreate(name="Python"))
    docker = await service.create(SkillCreate(name="Docker"))

    with pytest.raises(ConflictError):
        await service.update(docker.id, SkillUpdate(name="python"))
    assert docker.name == "Docker"


async def test_update_own_name_case_change_ok():
    service = SkillService(FakeSkillRepo())
    skill = await service.create(SkillCreate(name="docker"))

    updated = await service.update(skill.id, SkillUpdate(name="Docker"))
    assert updated.name == "Docker"
