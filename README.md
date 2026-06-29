# skill-tracker

Трекер задач и компетенций. Задачи привязаны к навыкам; при переходе задачи в
`done` исполнителю начисляется опыт по связанным навыкам, пересчитывается уровень,
событие пишется в журнал. Прогресс виден в профиле пользователя.

## Стек

FastAPI, SQLAlchemy 2.0 (async, asyncpg), Alembic, Pydantic v2, PostgreSQL, Redis,
React. Окружение — docker-compose. CI — GitHub Actions (ruff, pytest).

Требуется: Docker, docker-compose. Для локальной разработки вне контейнера —
Python 3.12+, Node.js 20+.

## Архитектура

Слоистая. Зависимости направлены строго вниз:

    API (routers)  -> приём HTTP, валидация, сериализация ответа
    Service        -> бизнес-логика (начисление опыта, проверки прав)
    Repository     -> доступ к данным, запросы к БД
    Models         -> SQLAlchemy ORM
    Schemas        -> Pydantic, валидация I/O

## Модель данных

    User          id, username, email, hashed_password, role, created_at
    Skill         id, name, description
    UserSkill     id, user_id, skill_id, experience, level        (User M:N Skill)
    Task          id, title, description, status, difficulty, deadline,
                  creator_id, assignee_id, created_at, updated_at
    TaskSkill     id, task_id, skill_id, exp_reward               (Task M:N Skill)
    ExperienceLog id, user_id, skill_id, task_id, amount, created_at

- `Task.status`: enum `todo | in_progress | review | done`
- `Task.difficulty`: int 1..5
- `UserSkill.level`: вычисляется от `experience` по порогам
- `User` ссылается на `Task` дважды: как creator и как assignee
- `ExperienceLog` — журнал начислений (append-only), нужен для пересчёта и отладки

Начисление опыта: `Task -> done` => сервис читает `TaskSkill` задачи => начисляет
`exp_reward` в `UserSkill` исполнителя => пишет `ExperienceLog` => пересчитывает level.

## Процесс разработки

Прямой пуш в `main` запрещён. Изменения — через PR: зелёный CI (ruff, pytest) и
минимум 1 апрув. Задачи — в Issues по шаблонам из `.github/ISSUE_TEMPLATE/`.
