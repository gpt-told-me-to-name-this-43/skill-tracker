import pytest

@pytest.mark.parametrize("xp, expected_level, expected_current, expected_next, expected_progress", [
    (0, 1, 0, 100, 0),
    (50, 1, 0, 100, 50),
    (99, 1, 0, 100, 99),
    (100, 2, 100, 200, 0),
    (200, 3, 200, 300, 0),
    (250, 3, 200, 300, 50),
])
def test_skill_progress_formula(xp, expected_level, expected_current, expected_next, expected_progress):
    """Проверка production-функции формулы прогресса навыка."""
    try:
        # Импорт находится внутри функции, чтобы не ломать запуск остальных тестов
        from app.services.skill_service import calculate_skill_progress
    except ImportError:
        pytest.fail("Баг реализации: Функция 'calculate_skill_progress' не найдена в app/services/skill_service.py!")

    result = calculate_skill_progress(xp)
    
    assert result["level"] == expected_level
    assert result["current_level_xp"] == expected_current
    assert result["next_level_xp"] == expected_next
    assert result["progress_to_next_level"] == expected_progress