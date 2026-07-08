from datetime import UTC, datetime

import pytest

from app.models.enums import TaskStatus
from app.models.experience import ExperienceLog
from app.models.skill import Skill
from app.models.task import Task, TaskSkill
from app.models.user import User, UserSkill
from app.schemas.experience import TaskSkillItem, TaskSkillsSet
from app.services.exceptions import NotFoundError
from app.services.experience import DefaultExperienceAwarder, ExperienceService
from app.services.task_service import TaskService


class FakeExperienceRepo:
    def __init__(self):
        self.task_skills: dict[int, list[TaskSkill]] = {}
        self.logs: list[ExperienceLog] = []
        self.user_skills: dict[tuple[int, int], UserSkill] = {}
        self.fail_on_create_log = False

    async def get_task_skills(self, task_id: int):
        return self.task_skills.get(task_id, [])

    async def set_task_skills(self, task_id: int, items):
        self.task_skills[task_id] = [
            task_skill(
                task_id=task_id,
                skill_id=item["skill_id"],
                exp_reward=item["exp_reward"],
            )
            for item in items
        ]
        return self.task_skills[task_id]

    async def get_log_entry(self, task_id: int, user_id: int, skill_id: int):
        return next(
            (
                log
                for log in self.logs
                if log.task_id == task_id and log.user_id == user_id and log.skill_id == skill_id
            ),
            None,
        )

    async def create_log_entry(self, user_id: int, skill_id: int, task_id: int, amount: int):
        if self.fail_on_create_log:
            raise RuntimeError("boom")
        log = ExperienceLog(
            id=len(self.logs) + 1,
            user_id=user_id,
            skill_id=skill_id,
            task_id=task_id,
            amount=amount,
            created_at=datetime.now(UTC),
        )
        self.logs.append(log)
        return log

    async def get_user_log(self, user_id: int, limit: int, offset: int):
        logs = [log for log in self.logs if log.user_id == user_id]
        return sorted(logs, key=lambda log: log.id, reverse=True)[offset : offset + limit]

    async def get_or_create_user_skill(self, user_id: int, skill_id: int):
        key = (user_id, skill_id)
        if key not in self.user_skills:
            self.user_skills[key] = UserSkill(
                id=len(self.user_skills) + 1,
                user_id=user_id,
                skill_id=skill_id,
                experience=0,
            )
        return self.user_skills[key]

    async def increment_user_skill_experience(self, user_skill: UserSkill, amount: int):
        user_skill.experience += amount
        return user_skill


class FakeTaskRepo:
    def __init__(self, tasks: list[Task]):
        self.tasks = {task.id: task for task in tasks}

    async def get_task_by_id(self, task_id: int):
        return self.tasks.get(task_id)

    async def set_status(self, task: Task, status: TaskStatus):
        task.status = status
        return task

    async def set_assignee(self, task: Task, assignee_id: int | None):
        task.assignee_id = assignee_id
        return task


class FakeSkillRepo:
    def __init__(self, skills: list[Skill]):
        self.skills = {skill.id: skill for skill in skills}

    async def get(self, skill_id: int):
        return self.skills.get(skill_id)


class FakeUserRepo:
    def __init__(self, users: list[User]):
        self.users = {user.id: user for user in users}

    async def get(self, user_id: int):
        return self.users.get(user_id)


def task(task_id: int, assignee_id: int | None = 10, status: TaskStatus = TaskStatus.todo) -> Task:
    return Task(
        id=task_id,
        title=f"Task {task_id}",
        status=status,
        difficulty=3,
        creator_id=1,
        assignee_id=assignee_id,
    )


def task_skill(task_id: int, skill_id: int, exp_reward: int) -> TaskSkill:
    item = TaskSkill(
        id=skill_id,
        task_id=task_id,
        skill_id=skill_id,
        exp_reward=exp_reward,
    )
    item.skill = Skill(id=skill_id, name=f"skill-{skill_id}", description=None)
    return item


def user(user_id: int) -> User:
    return User(id=user_id, username=f"user-{user_id}", email=f"user-{user_id}@example.test")


def skill(skill_id: int) -> Skill:
    return Skill(id=skill_id, name=f"skill-{skill_id}", description=None)


async def test_award_for_task_basic_adds_xp_and_log():
    repo = FakeExperienceRepo()
    repo.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]

    await DefaultExperienceAwarder(repo).award_for_task(task(1))

    assert repo.user_skills[(10, 1)].experience == 50
    assert [(log.user_id, log.skill_id, log.task_id, log.amount) for log in repo.logs] == [
        (10, 1, 1, 50)
    ]


async def test_award_for_task_multiple_skills_adds_xp_for_each_skill():
    repo = FakeExperienceRepo()
    repo.task_skills[1] = [
        task_skill(task_id=1, skill_id=1, exp_reward=50),
        task_skill(task_id=1, skill_id=2, exp_reward=30),
    ]

    await DefaultExperienceAwarder(repo).award_for_task(task(1))

    assert repo.user_skills[(10, 1)].experience == 50
    assert repo.user_skills[(10, 2)].experience == 30
    assert len(repo.logs) == 2


async def test_edge_u1_done_without_assignee_is_noop_and_logs_warning(caplog):
    repo = FakeExperienceRepo()
    repo.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]

    await DefaultExperienceAwarder(repo).award_for_task(task(1, assignee_id=None))

    assert repo.logs == []
    assert repo.user_skills == {}
    assert "without assignee" in caplog.text


async def test_edge_u5_task_without_rewards_is_noop():
    repo = FakeExperienceRepo()

    await DefaultExperienceAwarder(repo).award_for_task(task(1))

    assert repo.logs == []
    assert repo.user_skills == {}


async def test_edge_u4_repeat_award_is_idempotent():
    repo = FakeExperienceRepo()
    repo.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]
    awarder = DefaultExperienceAwarder(repo)

    await awarder.award_for_task(task(1))
    await awarder.award_for_task(task(1))

    assert repo.user_skills[(10, 1)].experience == 50
    assert len(repo.logs) == 1


async def test_edge_u6_award_creates_missing_user_skill():
    repo = FakeExperienceRepo()
    repo.task_skills[1] = [task_skill(task_id=1, skill_id=3, exp_reward=25)]

    assert (10, 3) not in repo.user_skills

    await DefaultExperienceAwarder(repo).award_for_task(task(1))

    assert repo.user_skills[(10, 3)].experience == 25


async def test_task_service_done_transition_awards_once_and_done_to_done_does_not_repeat():
    task_model = task(1)
    exp_repo = FakeExperienceRepo()
    exp_repo.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]
    service = TaskService(
        task_repo=FakeTaskRepo([task_model]),
        user_repo=FakeUserRepo([user(10)]),
        experience_awarder=DefaultExperienceAwarder(exp_repo),
    )

    await service.change_status(1, TaskStatus.done)
    await service.change_status(1, TaskStatus.done)

    assert task_model.status == TaskStatus.done
    assert exp_repo.user_skills[(10, 1)].experience == 50
    assert len(exp_repo.logs) == 1


async def test_edge_u2_reassign_after_done_does_not_move_xp():
    task_model = task(1)
    exp_repo = FakeExperienceRepo()
    exp_repo.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]
    service = TaskService(
        task_repo=FakeTaskRepo([task_model]),
        user_repo=FakeUserRepo([user(10), user(20)]),
        experience_awarder=DefaultExperienceAwarder(exp_repo),
    )

    await service.change_status(1, TaskStatus.done)
    await service.assign_task(1, 20)

    assert task_model.assignee_id == 20
    assert exp_repo.user_skills[(10, 1)].experience == 50
    assert (20, 1) not in exp_repo.user_skills
    assert exp_repo.logs[0].user_id == 10


async def test_edge_u3_award_error_is_propagated_to_request_transaction():
    task_model = task(1)
    exp_repo = FakeExperienceRepo()
    exp_repo.task_skills[1] = [task_skill(task_id=1, skill_id=1, exp_reward=50)]
    exp_repo.fail_on_create_log = True
    service = TaskService(
        task_repo=FakeTaskRepo([task_model]),
        user_repo=FakeUserRepo([user(10)]),
        experience_awarder=DefaultExperienceAwarder(exp_repo),
    )

    with pytest.raises(RuntimeError, match="boom"):
        await service.change_status(1, TaskStatus.done)

    assert exp_repo.logs == []


async def test_set_task_skills_replaces_rewards_and_checks_task_and_skills():
    exp_repo = FakeExperienceRepo()
    service = ExperienceService(
        experience_repo=exp_repo,
        task_repo=FakeTaskRepo([task(1)]),
        skill_repo=FakeSkillRepo([skill(1), skill(2)]),
        user_repo=FakeUserRepo([user(10)]),
    )

    result = await service.set_task_skills(
        1,
        TaskSkillsSet(
            skills=[
                TaskSkillItem(skill_id=1, exp_reward=50),
                TaskSkillItem(skill_id=2, exp_reward=30),
            ]
        ),
    )
    assert [item.exp_reward for item in result] == [50, 30]

    result = await service.set_task_skills(
        1,
        TaskSkillsSet(skills=[TaskSkillItem(skill_id=2, exp_reward=10)]),
    )
    assert [(item.skill_id, item.exp_reward) for item in result] == [(2, 10)]


async def test_set_task_skills_missing_skill_raises_not_found():
    service = ExperienceService(
        experience_repo=FakeExperienceRepo(),
        task_repo=FakeTaskRepo([task(1)]),
        skill_repo=FakeSkillRepo([]),
        user_repo=FakeUserRepo([user(10)]),
    )

    with pytest.raises(NotFoundError):
        await service.set_task_skills(
            1,
            TaskSkillsSet(skills=[TaskSkillItem(skill_id=404, exp_reward=50)]),
        )


async def test_get_user_log_checks_user_and_returns_newest_first():
    exp_repo = FakeExperienceRepo()
    exp_repo.logs = [
        ExperienceLog(id=1, user_id=10, skill_id=1, task_id=1, amount=10),
        ExperienceLog(id=2, user_id=10, skill_id=2, task_id=2, amount=20),
    ]
    service = ExperienceService(
        experience_repo=exp_repo,
        task_repo=FakeTaskRepo([]),
        skill_repo=FakeSkillRepo([]),
        user_repo=FakeUserRepo([user(10)]),
    )

    result = await service.get_user_log(user_id=10, limit=50, offset=0)

    assert [log.id for log in result] == [2, 1]
