import asyncio
from dataclasses import dataclass

from sqlalchemy import select

from app.core.database import async_session_maker
from app.core.security import hash_password
from app.models.user import User


@dataclass(frozen=True)
class DevUser:
    email: str
    username: str
    password: str
    role: str = "user"


DEV_USERS = (
    DevUser(
        email="test@example.com",
        username="test",
        password="password123",
    ),
)


async def seed_dev_users() -> list[str]:
    messages: list[str] = []

    async with async_session_maker() as session:
        for dev_user in DEV_USERS:
            existing_by_email = await session.scalar(
                select(User).where(User.email == dev_user.email)
            )
            if existing_by_email:
                username_owner = await session.scalar(
                    select(User).where(
                        User.username == dev_user.username,
                        User.id != existing_by_email.id,
                    )
                )
                if username_owner:
                    messages.append(f"skipped: username {dev_user.username!r} already exists")
                    continue

                existing_by_email.username = dev_user.username
                existing_by_email.hashed_password = hash_password(dev_user.password)
                existing_by_email.role = dev_user.role
                messages.append(f"updated: {dev_user.email}")
                continue

            existing_by_username = await session.scalar(
                select(User).where(User.username == dev_user.username)
            )
            if existing_by_username:
                messages.append(f"skipped: username {dev_user.username!r} already exists")
                continue

            session.add(
                User(
                    email=dev_user.email,
                    username=dev_user.username,
                    hashed_password=hash_password(dev_user.password),
                    role=dev_user.role,
                )
            )
            messages.append(f"created: {dev_user.email}")

        await session.commit()

    return messages


async def main() -> None:
    for message in await seed_dev_users():
        print(message)


if __name__ == "__main__":
    asyncio.run(main())
