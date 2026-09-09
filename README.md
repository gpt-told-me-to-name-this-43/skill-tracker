# skill-tracker

Трекер задач и компетенций. Задачи привязаны к навыкам; при переходе задачи в
`done` исполнителю начисляется опыт по связанным навыкам, пересчитывается уровень,
событие пишется в журнал. Прогресс виден в профиле пользователя.

## Стек

Backend — Java 21, Spring Boot 4.1, Spring Web MVC, Spring Data JPA (Hibernate),
Spring Security, Spring Validation, Spring Data Redis, PostgreSQL, Flyway.
Frontend — React + Vite. Окружение — docker compose. CI — GitHub Actions
(Spotless, JUnit 5, сборка образа, lint и build фронтенда).

Требуется: Docker, docker compose. Для локальной разработки вне контейнера —
JDK 21+ и Node.js 20+. Maven ставить не нужно: в `backend/` лежит Maven Wrapper.

## Архитектура

Слоистая. Зависимости направлены строго вниз:

    Controller     -> приём HTTP, валидация, сериализация ответа
    Service        -> бизнес-логика (начисление опыта, проверки прав)
    Repository     -> доступ к данным, запросы к БД (Spring Data JPA)
    Entity         -> JPA-модель
    DTO            -> Java records, валидация I/O

Исходники — в `backend/src/main/java/com/skilltracker`:

    controller/    HTTP-эндпоинты
    service/       бизнес-логика и мапперы ответов
    repository/    Spring Data JPA
    domain/        JPA-сущности и перечисления
    dto/           request/response records
    security/      JWT-аутентификация и правила доступа
    integration/   клиенты GitHub и OpenRouter
    config/        конфигурация Jackson, HTTP-клиентов, окружения
    exception/     доменные исключения и единый обработчик ошибок
    seed/          dev seed

## Модель данных

    User          id, username, email, hashed_password, role, avatar_url, position,
                  member_status, github_login, is_placeholder, created_at, updated_at
    Skill         id, name, description
    UserSkill     id, user_id, skill_id, experience                  (User M:N Skill)
    Task          id, title, description, status, difficulty, deadline,
                  creator_id, assignee_id, approved_by_id, approved_at,
                  github_issue_number, created_at, updated_at
    TaskSkill     id, task_id, skill_id, exp_reward                  (Task M:N Skill)
    ExperienceLog id, user_id, skill_id, task_id, amount, created_at
    Label, TaskLabel, TaskAttachment, TaskRelation, Team, TeamMember

- `Task.status`: enum `todo | in_progress | review | done` (нативный тип
  PostgreSQL `taskstatus`; те же значения уходят в JSON)
- `done` разрешён только из `review` после approval; задачу нельзя закрыть сразу
  из `todo` или `in_progress`
- `Task.difficulty`: int 1..5
- Уровень навыка вычисляется от `experience` (100 XP на уровень)
- `ExperienceLog` — журнал начислений (append-only). Уникальный ключ
  `(task_id, user_id, skill_id)` делает начисление идемпотентным

Начисление опыта: `Task -> done` => сервис читает `TaskSkill` задачи => начисляет
`exp_reward` в `UserSkill` исполнителя => пишет `ExperienceLog` => пересчитывает
уровень. Всё в одной транзакции, под блокировкой строки задачи, поэтому
параллельные запросы не задваивают XP.

## Локальный запуск

1. Скопировать переменные окружения:

       cp .env.example .env

2. Поднять сервисы:

       docker compose up --build

   API поднимается на `http://localhost:8000`. Миграции Flyway применяются при
   старте приложения, отдельного шага не требуется.

3. В другом терминале создать тестового пользователя и каталог навыков:

       docker compose run --rm -e SPRING_PROFILES_ACTIVE=seed api

   Профиль `seed` выполняет наполнение и завершает процесс. Команда идемпотентная:
   при повторном запуске она не создаёт дубли. Python для seed больше не нужен.

4. Открыть фронтенд отдельно через Vite:

       npm install
       npm run dev

   Фронтенд Vite обычно на `http://localhost:5173`, запросы `/api` он проксирует
   на `http://127.0.0.1:8000`.

Тестовый вход после seed-команды:

    email: test@example.com
    password: password123

### Запуск backend вне Docker

    cd backend
    ./mvnw spring-boot:run

Приложение читает `.env` из корня репозитория, поэтому достаточно поднять
PostgreSQL и Redis и указать в нём `DATABASE_URL` и `REDIS_URL`.

## Тесты

    cd backend && ./mvnw verify

`verify` прогоняет unit-тесты, MockMvc-тесты HTTP-контракта, интеграционные тесты
против настоящего PostgreSQL и проверку форматирования Spotless.

Интеграционные тесты по умолчанию поднимают PostgreSQL через Testcontainers, для
чего нужен доступный Docker. Если контейнеры недоступны, можно указать уже
работающий сервер:

    ./mvnw verify -Dtest.datasource.url=jdbc:postgresql://localhost:5432/app_test

То же самое можно задать переменными `TEST_DATASOURCE_URL`,
`TEST_DATASOURCE_USERNAME`, `TEST_DATASOURCE_PASSWORD` — CI использует именно их.

Фронтенд:

    npm ci && npm run lint && npm run build

## Миграции

Схемой управляет Flyway, скрипты лежат в
`backend/src/main/resources/db/migration`. Hibernate работает в режиме
`ddl-auto=validate` и только проверяет, что мэппинг соответствует схеме.

Поддерживаются оба сценария:

- **чистая база** — Flyway применяет `V1__baseline_schema.sql` и создаёт схему целиком;
- **база, созданную прежними Alembic-миграциями** — она непустая, поэтому Flyway
  делает baseline на версии 1, пропускает `V1` и не трогает существующие данные.
  Оставшаяся таблица `alembic_version` безвредна, её можно не удалять.

Новые изменения схемы добавляются как `V2__...sql`, `V3__...sql` и применяются в
обоих сценариях одинаково.

## Конфигурация

| Переменная                 | Назначение                                    | По умолчанию                    |
|----------------------------|-----------------------------------------------|---------------------------------|
| `DATABASE_URL`             | Подключение к PostgreSQL                      | —                               |
| `REDIS_URL`                | Подключение к Redis                           | —                               |
| `JWT_SECRET`               | Ключ подписи токенов, минимум 32 байта        | —                               |
| `JWT_ALGORITHM`            | Алгоритм подписи                              | `HS256`                         |
| `ACCESS_TOKEN_TTL_MINUTES` | Время жизни access-токена                     | `60`                            |
| `UPLOAD_DIR`               | Каталог для вложений, отдаётся на `/uploads`  | `uploads`                       |
| `GITHUB_REPO`              | Репозиторий для импорта issues                | `gpt-told-me-to-name-this-43/skill-tracker` |
| `GITHUB_TOKEN`             | Токен GitHub (необязательный)                 | —                               |
| `GITHUB_API_URL`           | База GitHub API                               | `https://api.github.com`        |
| `OPENROUTER_API_KEY`       | Ключ OpenRouter; без него `/tasks/analyze` отдаёт 503 | —                       |
| `OPENROUTER_MODEL`         | Модель OpenRouter                             | `poolside/laguna-xs-2.1:free`   |
| `OPENROUTER_API_URL`       | База OpenRouter API                           | `https://openrouter.ai/api/v1`  |
| `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY` | Прокси для исходящих запросов  | —                               |
| `PORT`                     | Порт HTTP-сервера                             | `8000`                          |

`DATABASE_URL` принимает как `postgresql://`, так и прежний
`postgresql+asyncpg://` — суффикс драйвера игнорируется.

## API

- Базовый префикс — `/api/v1`, health-check — `GET /health`, вложения —
  `GET /uploads/...`
- Авторизация — Bearer JWT: `POST /api/v1/auth/login` возвращает `access_token`
- OpenAPI-документ — `GET /openapi.json`, Swagger UI — `GET /docs`
- Формат ошибок единый: `{"error": {"message": "...", "details": null}}`

## Процесс разработки

Прямой пуш в `main` запрещён. Изменения — через PR: зелёный CI и минимум 1 апрув.
Задачи — в Issues по шаблонам из `.github/ISSUE_TEMPLATE/`.
