import pytest

from app.models.skill import Skill
from app.schemas.skill import SkillCreate
from app.services.exceptions import ConflictError, NotFoundError
from app.services.skill_service import SkillService


class FakeSkillRepo:
    def __init__(self):
        self._items: dict[int, Skill] = {}
        self._counter = 0

    async def get(self, skill_id):
        return self._items.get(skill_id)

    async def get_by_name(self, name):
        return next((s for s in self._items.values() if s.name == name), None)

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
