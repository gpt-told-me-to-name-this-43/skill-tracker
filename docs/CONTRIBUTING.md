## Референсный вертикальный срез

Эталон стиля — сущность `Skill`, реализованная через все слои.
Cтруктуры данных:

- Модель:      `app/models/skill.py`
- Схемы:       `app/schemas/skill.py`             (Create / Update / Read раздельно)
- Репозиторий: `app/repositories/skill_repo.py`   (только доступ к данным)
- Сервис:      `app/services/skill_service.py`    (только логика, без HTTP)
- Роутер:      `app/api/v1/skills.py`             (без запросов к БД)
- Тесты:       `tests/test_skill_service.py` (юнит) + `tests/test_skills_api.py` (интеграция)

### Границы слоёв
- В роутере нет `select` / `session` — только вызов сервиса.
- В сервисе нет `import fastapi` — ошибки через доменные исключения
  (`NotFoundError`, `ConflictError`) из `app/services/exceptions.py`.
- При изменении модели необходима миграция в том же PR
