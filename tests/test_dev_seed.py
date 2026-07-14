from app.core.security import hash_password, verify_password
from app.dev_seed import DEV_SKILLS, DEV_USERS


def test_dev_login_credentials_are_valid_for_auth_schema():
    user = DEV_USERS[0]

    assert user.email == "test@example.com"
    assert user.username == "test"
    assert len(user.password) >= 8
    assert verify_password(user.password, hash_password(user.password))


def test_dev_skills_fit_schema_and_are_unique_case_insensitively():
    names = [name for name, _ in DEV_SKILLS]

    assert len(names) == len({name.lower() for name in names})
    for name, description in DEV_SKILLS:
        assert 1 <= len(name.strip()) <= 100
        assert name == name.strip()
        assert description


def test_dev_skills_cover_core_competencies():
    names = {name for name, _ in DEV_SKILLS}

    assert names >= {
        "backend",
        "frontend",
        "database",
        "api_design",
        "devops",
        "testing",
        "documentation",
        "debugging",
    }
