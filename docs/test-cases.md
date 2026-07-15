# Тест-кейсы проекта

## Источник правды
Ожидаемое поведение взято из спек (Acceptance Criteria задач) и зафиксировано в `docs/decisions.md`. 
**Статусы:** pass / fail / blocked (очередь на прогон).

## 1. Auth & Users
| ID | Название | Предусловия | Шаги | Ожидаемый результат | Статус | Ссылка |
|---|---|---|---|---|---|---|
| AU-1 | Регистрация | БД пустая | POST /auth/register | 201 Created | blocked | spec:Auth |
| AU-2 | Дубль email | Юзер есть | POST /auth/register с тем же email | 409 Conflict | blocked | spec:Auth |
| AU-3 | Дубль username | Юзер есть | POST /auth/register с тем же username | 409 Conflict | blocked | spec:Auth |
| AU-4 | Нормализация email | Зареган `Dev@X.com` | POST /auth/login под `dev@x.com` | 200 OK, логин успешен | blocked | spec:Auth |
| AU-5 | Успешный логин | Есть аккаунт | POST /auth/login с валидными данными | 200 OK, выдан access_token | blocked | spec:Auth |
| AU-6 | Логин: неверный email | - | POST /auth/login с ошибкой в email | 401 Unauthorized | blocked | spec:Auth |
| AU-7 | Логин: неверный пароль | - | POST /auth/login с ошибкой в пароле | 401 Unauthorized | blocked | spec:Auth |
| AU-8 | 401 без уточнений | - | Логин с любой ошибкой | В ответе не раскрывается, что именно неверно | blocked | spec:Auth |
| AU-9 | GET /auth/me валидный | Авторизован | GET /auth/me | 200 OK, данные юзера | blocked | spec:Auth |
| AU-10 | GET /auth/me без токена | - | GET /auth/me без заголовка | 401 Unauthorized | blocked | spec:Auth |
| AU-11 | GET /auth/me битый токен| - | GET /auth/me с токеном "123" | 401 Unauthorized | blocked | spec:Auth |
| AU-12 | GET /auth/me истекший | Токен протух | GET /auth/me | 401 Unauthorized | blocked | spec:Auth |
| AU-13 | Скрытый пароль | - | Запрос данных юзера | В ответе нет поля `hashed_password` | blocked | spec:Auth |
| AU-14 | GET /users пагинация | Есть юзеры | GET /users?limit=2 | Возвращает список из 2 юзеров | blocked | spec:Auth |
| AU-15 | GET /users/{id} | Юзер ID=1 есть| GET /users/1 | 200 OK, данные пользователя | blocked | spec:Auth |
| AU-16 | GET /users/{id} 404 | Юзера нет | GET /users/999 | 404 Not Found | blocked | spec:Auth |

## 2. Skills
| ID | Название | Предусловия | Шаги | Ожидаемый результат | Статус | Ссылка |
|---|---|---|---|---|---|---|
| SK-1 | GET /skills | - | GET /skills | 200 OK, список навыков | blocked | spec:Skills |
| SK-2 | POST /skills | - | POST /skills {name: "Python"} | 201 Created | blocked | spec:Skills |
| SK-3 | Дубль name | Навык "Python" | POST /skills {name: "Python"} | 409 Conflict | blocked | spec:Skills |
| SK-4 | Дубль в другом регистре | Навык "Python" | POST /skills {name: "PYTHON"} | 409 Conflict | blocked | spec:Skills |
| SK-5 | Назначение навыка | - | POST /users/{id}/skills | 201/200 OK | blocked | spec:Skills |
| SK-6 | Повторное назначение | Навык выдан | POST /users/{id}/skills тот же | 409 Conflict | blocked | spec:Skills |
| SK-7 | Получение навыков юзера | Есть навык | GET /users/{id}/skills | 200 OK, список навыков | blocked | spec:Skills |
| SK-8 | Получение прогресса | - | GET /users/{id}/progress | 200 OK, поля прогресса присутствуют | blocked | spec:Skills |
| SK-9 | Юзер без навыков | Навыков нет | GET /users/{id}/skills и GET /users/{id}/progress | `/skills`: 200 OK, `[]`; `/progress`: 200 OK, нули в агрегатах | blocked | decisions |
| SK-10| Юзер 404 | Юзера нет | Запросить скиллы ID=999 | 404 Not Found | blocked | spec:Skills |
| SK-11| Навык 404 | Навыка нет | Назначить скилл ID=999 | 404 Not Found | blocked | spec:Skills |
| SK-12| Формула: exp=0 | - | Проверить прогресс | level=1, progress=0 | blocked | spec:Skills |
| SK-13| Формула: exp=50 | - | Проверить прогресс | level=1, progress=50 | blocked | spec:Skills |
| SK-14| Формула: exp=99 | - | Проверить прогресс | level=1, progress=99 | blocked | spec:Skills |
| SK-15| Формула: exp=100| - | Проверить прогресс | level=2, progress=0 | blocked | spec:Skills |
| SK-16| Формула: exp=200| - | Проверить прогресс | level=3, progress=0 | blocked | spec:Skills |
| SK-17| Формула: exp=250| - | Проверить прогресс | level=3, progress=50| blocked | spec:Skills |

## 3. Tasks
| ID | Название | Предусловия | Шаги | Ожидаемый результат | Статус | Ссылка |
|---|---|---|---|---|---|---|
| TA-1 | Создание задачи | Авторизован | POST /tasks | 201, `creator_id` берется из current_user | blocked | spec:Tasks |
| TA-2 | Дедлайн в прошлом | - | POST /tasks deadline=вчера | 400 Bad Request | blocked | spec:Tasks |
| TA-3 | Difficulty вне 1..5 | - | POST /tasks difficulty=6 | 422 Unprocessable Entity | blocked | spec:Tasks |
| TA-4 | Список и фильтр status | Задачи есть | GET /tasks?status=todo | Отдаются только todo | blocked | spec:Tasks |
| TA-5 | Фильтр assignee_id | Задачи есть | GET /tasks?assignee_id=1 | Отдаются задачи юзера | blocked | spec:Tasks |
| TA-6 | Фильтр difficulty | Задачи есть | GET /tasks?difficulty=3 | Отдаются задачи diff=3 | blocked | spec:Tasks |
| TA-7 | Комбинация фильтров | Задачи есть | GET /tasks с 3 фильтрами | Возвращается пересечение | blocked | spec:Tasks |
| TA-8 | GET задачи по id | Задача есть | GET /tasks/1 | 200 OK, данные задачи | blocked | spec:Tasks |
| TA-9 | Задача 404 | Задачи нет | GET /tasks/999 | 404 Not Found | blocked | spec:Tasks |
| TA-10| PATCH задачи (обычные) | Задача есть | PATCH /tasks/1 {title:"New"} | 200 OK, title изменен | blocked | spec:Tasks |
| TA-11| PATCH игнорит status | - | PATCH /tasks/1 {status:"done"} | Status не меняется | blocked | spec:Tasks |
| TA-12| Изменение status | - | PATCH /tasks/1/status | 200 OK, статус изменен | blocked | spec:Tasks |
| TA-13| Назначение исполнителя | - | PATCH /tasks/1/assign {id:2} | 200 OK, assignee назначен | blocked | spec:Tasks |
| TA-14| Снятие исполнителя | Назначен | PATCH /tasks/1/assign {id:null}| 200 OK, assignee снят | blocked | spec:Tasks |
| TA-15| Исполнитель 404 | Юзера нет | PATCH /tasks/1/assign {id:999}| 404 Not Found | blocked | spec:Tasks |

## 4. Experience (Включает У1-У6)
Во всех кейсах с `PATCH status=done` предусловие: задача в статусе `review` и апрувнута (`PATCH /tasks/{id}/approve`); иначе API возвращает 400 (см. decisions «Workflow задач»).

| ID | Название | Предусловия | Шаги | Ожидаемый результат | Статус | Ссылка |
|---|---|---|---|---|---|---|
| EX-1 | MVP сквозной сценарий | Задача с исп. и наградой, в review, апрувнута | PATCH статус в done | Рост XP, пополнение ExperienceLog, обновление профиля | blocked | spec:Exp |
| EX-2 | PUT задает награды | - | PUT /tasks/1/skills | Награды заданы | blocked | spec:Exp |
| EX-3 | PUT заменяет награды | Награды есть | Повторный PUT | Полная замена наград | blocked | spec:Exp |
| EX-4 | PUT с `[]` | Награды есть | PUT /tasks/1/skills `[]` | Все награды сняты | blocked | spec:Exp |
| EX-5 | PUT с дублями skill_id | - | PUT дублирующиеся ID скиллов | 422 Unprocessable Entity | blocked | spec:Exp |
| EX-6 | PUT exp_reward=0 | - | PUT с exp=0 | 422 Unprocessable Entity | blocked | spec:Exp |
| EX-7 | PUT exp_reward>1000 | - | PUT с exp=1001 | 422 Unprocessable Entity | blocked | spec:Exp |
| EX-8 | PUT skill 404 | Скилла нет | PUT с skill_id=999 | 404 Not Found | blocked | spec:Exp |
| EX-9 | GET награды задачи | Награды есть | GET /tasks/1/skills | 200 OK, список наград | blocked | spec:Exp |
| EX-10| GET награды 404 | Задачи нет | GET /tasks/999/skills | 404 Not Found | blocked | spec:Exp |
| EX-11| GET ExperienceLog | Был начислен XP | GET /users/1/experience-log | 200 OK, история начислений | blocked | spec:Exp |
| EX-12| GET ExpLog 404 | Юзера нет | GET /users/999/experience-log| 404 Not Found | blocked | spec:Exp |
| EX-13| **У1** done без исп. | assignee=null, задача в review, апрувнута | PATCH status=done | 200, XP не начислен, лог пуст | blocked | decisions |
| EX-14| **У2** переназначение | Задача done | Сменить assignee_id | XP остается у старого исп. | blocked | decisions |
| EX-15| **У3** транзакционность| - | *Руками невозможно уронить* | У3 — покрыт автотестом, ссылка на Experience-issue | pass | decisions |
| EX-16| **У4** идемпотентность | Задача done | Повторный PATCH status=done | XP не задваивается, лог не растет | blocked | decisions |
| EX-17| **У5** задача без наград | Наград нет, задача в review, апрувнута | PATCH status=done | 200, XP не начисляется | blocked | decisions |
| EX-18| **У6** нет UserSkill | Навыка у исп. нет, задача в review, апрувнута | PATCH status=done (с наградой)| Навык появляется в профиле с XP | blocked | decisions |

## 5. Frontend
| ID | Название | Предусловия | Шаги | Ожидаемый результат | Статус | Ссылка |
|---|---|---|---|---|---|---|
| FR-1 | Логин / Ошибка / Успех | - | Пройти сценарий входа | UI корректно обрабатывает состояния | blocked | spec:Front |
| FR-2 | Редирект 401 | Без токена | Открыть приватную страницу | Редирект на /login | blocked | spec:Front |
| FR-3 | UI Задач: States | - | Проверить loading, empty, error| Соответствующие стейты отображены | blocked | spec:Front |
| FR-4 | Фильтры задач UI | Задачи есть | Применить фильтры в UI | Список фильтруется | blocked | spec:Front |
| FR-5 | Создание задачи | - | Заполнить форму | Валидация работает, выбор исп. из списка | blocked | spec:Front |
| FR-6 | Карточка и Статус | Задача есть | Открыть карточку, сменить статус | Статус меняется, отображается done | blocked | spec:Front |
| FR-7 | Профиль: Прогресс | Есть опыт | Открыть профиль | Progress bar использует progress_to_next_level | blocked | spec:Front |
| FR-8 | Проверка расчета UI | - | Сравнить UI и ответ API | Значения level/progress не считаются фронтом, а совпадают с API | blocked | spec:Front |
| FR-9 | Профиль: Empty state | Навыков нет | Открыть профиль | Отображается empty-state | blocked | spec:Front |
| FR-10| UI: 409 Conflict | - | Вызвать конфликт (дубль email)| Отображение понятного сообщения | blocked | spec:Front |