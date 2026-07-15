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
                  creator_id, assignee_id, approved_by_id, approved_at,
                  created_at, updated_at
    TaskSkill     id, task_id, skill_id, exp_reward               (Task M:N Skill)
    ExperienceLog id, user_id, skill_id, task_id, amount, created_at

- `Task.status`: enum `todo | in_progress | review | done`
- `done` разрешён только из `review` после approval; задачу нельзя закрыть сразу
  из `todo` или `in_progress`
- `Task.difficulty`: int 1..5
- `UserSkill.level`: вычисляется от `experience` по порогам
- `User` ссылается на `Task` дважды: как creator и как assignee
- `ExperienceLog` — журнал начислений (append-only), нужен для пересчёта и отладки

Начисление опыта: `Task -> done` => сервис читает `TaskSkill` задачи => начисляет
`exp_reward` в `UserSkill` исполнителя => пишет `ExperienceLog` => пересчитывает level.
Для начисления задачу сначала нужно перевести в `review`, апрувнуть, затем перевести
в `done`.

## Процесс разработки

Прямой пуш в `main` запрещён. Изменения — через PR: зелёный CI (ruff, pytest) и
минимум 1 апрув. Задачи — в Issues по шаблонам из `.github/ISSUE_TEMPLATE/`.

## Документация проекта
Вся подробная информация по проекту (сущности, тест-кейсы, отчеты) вынесена в директорию `docs/`:
- [Технические решения и логика (Decisions)](docs/decisions.md)
- [Описание сущностей и MVP-сценарий](docs/entities.md)
- [QA Тест-кейсы](docs/test-cases.md)
- [Итоговый QA-отчет](docs/qa-report.md)

## Локальный запуск

1. Скопировать переменные окружения:

       cp .env.example .env

2. Поднять сервисы:

       docker compose up --build

3. В другом терминале создать тестового пользователя:

       docker compose exec api python -m app.dev_seed

   Команда идемпотентная: при повторном запуске она не создаёт дубли.

4. Открыть фронтенд отдельно через Vite:

       npm install
       npm run dev

   API доступно на `http://localhost:8000`, фронтенд Vite обычно на
   `http://localhost:5173`.

Тестовый вход после seed-команды:

    email: test@example.com
    password: password123
