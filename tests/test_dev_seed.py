from app.core.security import hash_password, verify_password
from app.dev_seed import DEV_USERS


def test_dev_login_credentials_are_valid_for_auth_schema():
    user = DEV_USERS[0]

    assert user.email == "test@example.com"
    assert user.username == "test"
    assert len(user.password) >= 8
    assert verify_password(user.password, hash_password(user.password))
