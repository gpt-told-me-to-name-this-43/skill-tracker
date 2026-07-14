from datetime import UTC, datetime, timedelta

from app.core.time_utils import to_naive_utc
from app.models.enums import TaskStatus
from app.repositories.experience_repo import ExperienceRepository
from app.repositories.task_repo import TaskRepository
from app.schemas.task import LintSeverity, TaskLintReport, TaskLintWarningRead
from app.services.exceptions import NotFoundError

TITLE_MIN_LENGTH = 8
TITLE_MIN_WORDS = 2
DESCRIPTION_MIN_LENGTH = 30
DEADLINE_SOON_WINDOW = timedelta(hours=24)
XP_MIN_PER_DIFFICULTY = 10
XP_MAX_PER_DIFFICULTY = 100
SUSPICIOUS_SINGLE_REWARD = 500

SEVERITY_INFO: LintSeverity = "info"
SEVERITY_WARNING: LintSeverity = "warning"


def lint_task_fields(
    *,
    title: str,
    description: str | None,
    status: TaskStatus,
    difficulty: int,
    deadline: datetime | None,
    assignee_id: int | None,
    label_count: int,
    exp_rewards: list[int],
    now: datetime | None = None,
) -> list[TaskLintWarningRead]:
    """Чистая проверка качества задачи над простыми значениями, без БД."""
    now = now or datetime.now(UTC).replace(tzinfo=None)
    warnings: list[TaskLintWarningRead] = []

    clean_title = title.strip()
    if len(clean_title) < TITLE_MIN_LENGTH:
        warnings.append(
            TaskLintWarningRead(
                code="title_too_short",
                field="title",
                severity=SEVERITY_WARNING,
                message=f"Title is shorter than {TITLE_MIN_LENGTH} characters",
            )
        )
    elif len(clean_title.split()) < TITLE_MIN_WORDS:
        warnings.append(
            TaskLintWarningRead(
                code="title_not_descriptive",
                field="title",
                severity=SEVERITY_INFO,
                message=f"Title has fewer than {TITLE_MIN_WORDS} words",
            )
        )

    clean_description = (description or "").strip()
    if not clean_description:
        warnings.append(
            TaskLintWarningRead(
                code="description_missing",
                field="description",
                severity=SEVERITY_WARNING,
                message="Task has no description",
            )
        )
    elif len(clean_description) < DESCRIPTION_MIN_LENGTH:
        warnings.append(
            TaskLintWarningRead(
                code="description_too_short",
                field="description",
                severity=SEVERITY_INFO,
                message=f"Description is shorter than {DESCRIPTION_MIN_LENGTH} characters",
            )
        )

    if not exp_rewards:
        warnings.append(
            TaskLintWarningRead(
                code="no_skill_rewards",
                field="skills",
                severity=SEVERITY_WARNING,
                message="Task has no skill rewards; completing it will award no XP",
            )
        )

    if status != TaskStatus.done:
        if deadline is None:
            warnings.append(
                TaskLintWarningRead(
                    code="no_deadline",
                    field="deadline",
                    severity=SEVERITY_INFO,
                    message="Task has no deadline",
                )
            )
        else:
            clean_deadline = to_naive_utc(deadline)
            if clean_deadline < now:
                warnings.append(
                    TaskLintWarningRead(
                        code="deadline_past",
                        field="deadline",
                        severity=SEVERITY_WARNING,
                        message="Deadline is in the past",
                    )
                )
            elif clean_deadline < now + DEADLINE_SOON_WINDOW:
                warnings.append(
                    TaskLintWarningRead(
                        code="deadline_soon",
                        field="deadline",
                        severity=SEVERITY_INFO,
                        message="Deadline is less than 24 hours away",
                    )
                )

    if status == TaskStatus.in_progress and assignee_id is None:
        warnings.append(
            TaskLintWarningRead(
                code="in_progress_without_assignee",
                field="assignee_id",
                severity=SEVERITY_WARNING,
                message="Task is in progress but has no assignee",
            )
        )

    if exp_rewards:
        total_reward = sum(exp_rewards)
        if total_reward < difficulty * XP_MIN_PER_DIFFICULTY:
            warnings.append(
                TaskLintWarningRead(
                    code="xp_below_difficulty",
                    field=None,
                    severity=SEVERITY_INFO,
                    message=(
                        f"Total XP reward {total_reward} looks low " f"for difficulty {difficulty}"
                    ),
                )
            )
        elif total_reward > difficulty * XP_MAX_PER_DIFFICULTY:
            warnings.append(
                TaskLintWarningRead(
                    code="xp_above_difficulty",
                    field=None,
                    severity=SEVERITY_WARNING,
                    message=(
                        f"Total XP reward {total_reward} looks high " f"for difficulty {difficulty}"
                    ),
                )
            )
        if any(reward > SUSPICIOUS_SINGLE_REWARD for reward in exp_rewards):
            warnings.append(
                TaskLintWarningRead(
                    code="suspicious_reward",
                    field=None,
                    severity=SEVERITY_WARNING,
                    message=(f"A single skill reward exceeds {SUSPICIOUS_SINGLE_REWARD} XP"),
                )
            )

    if label_count == 0:
        warnings.append(
            TaskLintWarningRead(
                code="no_labels",
                field="labels",
                severity=SEVERITY_INFO,
                message="Task has no labels",
            )
        )

    return warnings


class TaskLintService:
    def __init__(
        self,
        task_repo: TaskRepository,
        experience_repo: ExperienceRepository,
    ) -> None:
        self.task_repo = task_repo
        self.experience_repo = experience_repo

    async def lint_task(self, task_id: int) -> TaskLintReport:
        """Проверяет качество задачи; ничего не блокирует и не пишет в БД."""
        task = await self.task_repo.get_task_with_labels(task_id)
        if not task:
            raise NotFoundError(f"Task with id {task_id} not found")

        task_skills = await self.experience_repo.get_task_skills(task_id)
        return TaskLintReport(
            task_id=task_id,
            warnings=lint_task_fields(
                title=task.title,
                description=task.description,
                status=task.status,
                difficulty=task.difficulty,
                deadline=task.deadline,
                assignee_id=task.assignee_id,
                label_count=len(task.labels),
                exp_rewards=[task_skill.exp_reward for task_skill in task_skills],
            ),
        )
