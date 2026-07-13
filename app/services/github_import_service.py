"""Односторонняя синхронизация GitHub issues -> задачи.

Осознанное отступление от workflow (см. docs/decisions.md): закрытый на GitHub
issue переводит задачу в done напрямую, без review/approve и без начисления XP —
awarder здесь не вызывается, ExperienceLog не затрагивается. Переоткрытие issue
статус задачи не понижает.
"""

import secrets

from app.core.security import hash_password
from app.integrations.github_client import GitHubIssue, GitHubIssueSource
from app.models.enums import TaskStatus
from app.models.task import Task
from app.repositories.task_repo import TaskRepository
from app.repositories.user_repo import UserRepository

_TITLE_MAX_LENGTH = 200


class GitHubImportService:
    def __init__(
        self,
        source: GitHubIssueSource,
        task_repo: TaskRepository,
        user_repo: UserRepository,
    ) -> None:
        self.source = source
        self.task_repo = task_repo
        self.user_repo = user_repo

    async def sync(self, current_user_id: int) -> dict:
        issues = await self.source.fetch_issues()

        created = 0
        updated = 0
        users_created = 0
        assignee_cache: dict[str, int] = {}
        label_cache: dict[str, int] = {}

        for issue in issues:
            assignee_id: int | None = None
            if issue.assignee_login is not None:
                assignee_id, was_created = await self._resolve_assignee(
                    issue.assignee_login, assignee_cache
                )
                if was_created:
                    users_created += 1

            label_ids = [await self._resolve_label(name, label_cache) for name in issue.labels]

            task = await self.task_repo.get_task_by_github_issue_number(issue.number)
            if task is None:
                await self._create_task(issue, assignee_id, label_ids, current_user_id)
                created += 1
            elif await self._update_task(task, issue, assignee_id, label_ids):
                updated += 1

        return {"created": created, "updated": updated, "users_created": users_created}

    async def _create_task(
        self,
        issue: GitHubIssue,
        assignee_id: int | None,
        label_ids: list[int],
        creator_id: int,
    ) -> None:
        task = await self.task_repo.create_task(
            {
                "title": issue.title[:_TITLE_MAX_LENGTH],
                "description": issue.body,
                "status": self._map_status(issue.state),
                "assignee_id": assignee_id,
                "github_issue_number": issue.number,
            },
            creator_id=creator_id,
        )
        if label_ids:
            await self.task_repo.replace_task_labels(task.id, label_ids)

    async def _update_task(
        self,
        task: Task,
        issue: GitHubIssue,
        assignee_id: int | None,
        label_ids: list[int],
    ) -> bool:
        fields: dict = {}

        title = issue.title[:_TITLE_MAX_LENGTH]
        if task.title != title:
            fields["title"] = title
        if task.description != issue.body:
            fields["description"] = issue.body

        # GitHub может только закрывать: переоткрытие статус не понижает.
        if issue.state == "closed" and task.status != TaskStatus.done:
            fields["status"] = TaskStatus.done

        # Снятого на GitHub исполнителя не трогаем — не затираем локальные назначения.
        if assignee_id is not None and task.assignee_id != assignee_id:
            fields["assignee_id"] = assignee_id

        changed = False
        if fields:
            await self.task_repo.update_task(task, fields)
            changed = True

        current_label_ids = {task_label.label_id for task_label in task.labels}
        if current_label_ids != set(label_ids):
            await self.task_repo.replace_task_labels(task.id, label_ids)
            changed = True

        return changed

    @staticmethod
    def _map_status(state: str) -> TaskStatus:
        return TaskStatus.done if state == "closed" else TaskStatus.todo

    async def _resolve_assignee(self, login: str, cache: dict[str, int]) -> tuple[int, bool]:
        if login in cache:
            return cache[login], False

        user = await self.user_repo.get_user_by_github_login(login)
        if user is not None:
            cache[login] = user.id
            return user.id, False

        username = login
        suffix = 1
        while await self._identity_taken(username):
            suffix += 1
            username = f"{login}-{suffix}"

        user = await self.user_repo.create_placeholder_user(
            github_login=login,
            # Реальная noreply-конвенция GitHub; вход таким профилем невозможен.
            email=f"{username}@users.noreply.github.com",
            username=username,
            hashed_password=hash_password(secrets.token_urlsafe(32)),
        )
        cache[login] = user.id
        return user.id, True

    async def _identity_taken(self, username: str) -> bool:
        if await self.user_repo.get_user_by_username(username) is not None:
            return True
        email = f"{username}@users.noreply.github.com"
        return await self.user_repo.get_user_by_email(email) is not None

    async def _resolve_label(self, name: str, cache: dict[str, int]) -> int:
        key = name.lower()
        if key in cache:
            return cache[key]

        label = await self.task_repo.get_label_by_name_ci(name)
        if label is None:
            label = await self.task_repo.create_label(name, None)
        cache[key] = label.id
        return label.id
