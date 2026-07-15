"""Тесты односторонней синхронизации GitHub issues -> задачи (sqlite + реальные репозитории)."""

from sqlalchemy import func, select

from app.integrations.github_client import GitHubIssue
from app.models import ExperienceLog, Label, Task, User
from app.models.enums import TaskStatus
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository
from app.services.github_import_service import GitHubImportService


class FakeIssueSource:
    def __init__(self, issues: list[GitHubIssue]):
        self.issues = issues

    async def fetch_issues(self) -> list[GitHubIssue]:
        return list(self.issues)


def _issue(number: int, **overrides) -> GitHubIssue:
    fields = {
        "number": number,
        "title": f"Issue {number}",
        "body": f"Body {number}",
        "state": "open",
        "assignee_login": None,
        "labels": [],
    }
    fields.update(overrides)
    return GitHubIssue(**fields)


def _service(session, issues: list[GitHubIssue]) -> GitHubImportService:
    return GitHubImportService(
        source=FakeIssueSource(issues),
        task_repo=TaskRepository(session),
        user_repo=UserRepository(session),
    )


async def _seed_importer(session) -> User:
    importer = User(
        username="importer",
        email="importer@example.com",
        hashed_password="hashed",
    )
    session.add(importer)
    await session.flush()
    return importer


async def test_sync_maps_open_and_closed_issues(db_session):
    importer = await _seed_importer(db_session)
    service = _service(
        db_session,
        [
            _issue(1, title="Open issue", body="Open body"),
            _issue(2, state="closed"),
        ],
    )

    result = await service.sync(importer.id)

    assert result == {"created": 2, "updated": 0, "users_created": 0}
    open_task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    closed_task = await db_session.scalar(select(Task).where(Task.github_issue_number == 2))
    assert open_task.status == TaskStatus.todo
    assert open_task.title == "Open issue"
    assert open_task.description == "Open body"
    assert open_task.creator_id == importer.id
    assert open_task.difficulty == 3
    assert closed_task.status == TaskStatus.done


async def test_sync_closed_issue_awards_no_xp_and_skips_approval(db_session):
    importer = await _seed_importer(db_session)
    service = _service(db_session, [_issue(1, state="closed", assignee_login="octocat")])

    await service.sync(importer.id)

    task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    assert task.status == TaskStatus.done
    assert task.approved_at is None
    assert task.approved_by_id is None
    assert await db_session.scalar(select(func.count()).select_from(ExperienceLog)) == 0


async def test_sync_creates_placeholder_user_once_and_reuses_it(db_session):
    importer = await _seed_importer(db_session)
    issues = [
        _issue(1, assignee_login="octocat"),
        _issue(2, assignee_login="octocat"),
    ]

    result = await _service(db_session, issues).sync(importer.id)

    assert result["users_created"] == 1
    ghost = await db_session.scalar(select(User).where(User.github_login == "octocat"))
    assert ghost.is_placeholder is True
    assert ghost.username == "octocat"
    assert ghost.email == "octocat@users.noreply.github.com"
    tasks = (await db_session.scalars(select(Task).order_by(Task.github_issue_number))).all()
    assert [task.assignee_id for task in tasks] == [ghost.id, ghost.id]

    # Повторный запуск переиспользует профиль, а не создаёт дубликат.
    result = await _service(db_session, issues).sync(importer.id)
    assert result["users_created"] == 0
    ghost_count = await db_session.scalar(
        select(func.count()).select_from(User).where(User.github_login == "octocat")
    )
    assert ghost_count == 1


async def test_sync_existing_user_with_github_login_is_not_duplicated(db_session):
    importer = await _seed_importer(db_session)
    linked = User(
        username="real-dev",
        email="dev@example.com",
        hashed_password="hashed",
        github_login="octocat",
    )
    db_session.add(linked)
    await db_session.flush()

    result = await _service(db_session, [_issue(1, assignee_login="octocat")]).sync(importer.id)

    assert result["users_created"] == 0
    task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    assert task.assignee_id == linked.id


async def test_sync_username_collision_gets_suffix(db_session):
    importer = await _seed_importer(db_session)
    db_session.add(User(username="octocat", email="taken@example.com", hashed_password="hashed"))
    await db_session.flush()

    result = await _service(db_session, [_issue(1, assignee_login="octocat")]).sync(importer.id)

    assert result["users_created"] == 1
    ghost = await db_session.scalar(select(User).where(User.github_login == "octocat"))
    assert ghost.username == "octocat-2"
    assert ghost.email == "octocat-2@users.noreply.github.com"


async def test_sync_is_idempotent_without_remote_changes(db_session):
    importer = await _seed_importer(db_session)
    issues = [
        _issue(1, labels=["bug"]),
        _issue(2, state="closed", assignee_login="octocat"),
    ]

    first = await _service(db_session, issues).sync(importer.id)
    second = await _service(db_session, issues).sync(importer.id)

    assert first == {"created": 2, "updated": 0, "users_created": 1}
    assert second == {"created": 0, "updated": 0, "users_created": 0}
    assert await db_session.scalar(select(func.count()).select_from(Task)) == 2


async def test_sync_issue_closed_between_syncs_moves_task_to_done_without_xp(db_session):
    importer = await _seed_importer(db_session)
    await _service(db_session, [_issue(1)]).sync(importer.id)

    result = await _service(db_session, [_issue(1, state="closed")]).sync(importer.id)

    assert result == {"created": 0, "updated": 1, "users_created": 0}
    task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    assert task.status == TaskStatus.done
    assert task.approved_at is None
    assert await db_session.scalar(select(func.count()).select_from(ExperienceLog)) == 0


async def test_sync_reopened_issue_keeps_task_done(db_session):
    importer = await _seed_importer(db_session)
    await _service(db_session, [_issue(1, state="closed")]).sync(importer.id)

    result = await _service(db_session, [_issue(1, state="open")]).sync(importer.id)

    assert result == {"created": 0, "updated": 0, "users_created": 0}
    task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    assert task.status == TaskStatus.done


async def test_sync_updates_changed_fields(db_session):
    importer = await _seed_importer(db_session)
    await _service(db_session, [_issue(1, title="Old", body="Old body")]).sync(importer.id)

    result = await _service(
        db_session,
        [_issue(1, title="New", body="New body", assignee_login="octocat")],
    ).sync(importer.id)

    assert result == {"created": 0, "updated": 1, "users_created": 1}
    task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    assert task.title == "New"
    assert task.description == "New body"
    assert task.assignee_id is not None


async def test_sync_reuses_labels_case_insensitively(db_session):
    importer = await _seed_importer(db_session)
    db_session.add(Label(name="Backend", color="#3B82F6"))
    await db_session.flush()

    await _service(db_session, [_issue(1, labels=["backend", "bug"])]).sync(importer.id)

    labels = (await db_session.scalars(select(Label))).all()
    assert sorted(label.name for label in labels) == ["Backend", "bug"]
    task = await TaskRepository(db_session).get_task_by_github_issue_number(1)
    linked_names = sorted(task_label.label.name for task_label in task.labels)
    assert linked_names == ["Backend", "bug"]


async def test_sync_truncates_long_titles(db_session):
    importer = await _seed_importer(db_session)
    long_title = "x" * 250

    await _service(db_session, [_issue(1, title=long_title)]).sync(importer.id)

    task = await db_session.scalar(select(Task).where(Task.github_issue_number == 1))
    assert task.title == "x" * 200
