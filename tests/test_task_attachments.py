import pytest
from pydantic import ValidationError
from sqlalchemy import func, select, text

from app.models import TaskAttachment
from app.schemas.task import TaskAttachmentCreate
from app.services.exceptions import NotFoundError
from tests.conftest import build_task_service, seed_task_fixtures


async def _add(service, task_id: int, user_id: int, name: str, url: str) -> dict:
    return await service.create_attachment(
        task_id,
        TaskAttachmentCreate(name=name, url=url),
        created_by_id=user_id,
    )


async def test_create_attachment_records_author_and_hides_email(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    attachment = await _add(
        service,
        fixtures.task.id,
        fixtures.creator.id,
        "Kanban mockup",
        "https://www.figma.com/example",
    )

    assert attachment["name"] == "Kanban mockup"
    assert attachment["url"] == "https://www.figma.com/example"
    assert attachment["created_by"]["id"] == fixtures.creator.id
    assert attachment["created_by"]["username"] == "creator"
    assert attachment["created_by"]["position"] == "Team Lead"
    assert "email" not in attachment["created_by"]


async def test_list_attachments_returns_creation_order(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await _add(service, fixtures.task.id, fixtures.creator.id, "First", "https://example.com/1")
    await _add(service, fixtures.task.id, fixtures.assignee.id, "Second", "https://example.com/2")

    attachments = await service.list_attachments(fixtures.task.id)

    assert [item["name"] for item in attachments] == ["First", "Second"]
    assert [item["created_by"]["username"] for item in attachments] == ["creator", "developer"]


async def test_attachments_count_appears_on_task_list_item(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await _add(service, fixtures.task.id, fixtures.creator.id, "First", "https://example.com/1")
    await _add(service, fixtures.task.id, fixtures.creator.id, "Second", "https://example.com/2")

    detail = await service.get_task_by_id(fixtures.task.id)

    assert detail["attachments_count"] == 2
    assert len(detail["attachments"]) == 2


async def test_delete_attachment_removes_it(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    attachment = await _add(
        service, fixtures.task.id, fixtures.creator.id, "Doc", "https://example.com/doc"
    )

    await service.delete_attachment(fixtures.task.id, attachment["id"])

    assert await service.list_attachments(fixtures.task.id) == []


async def test_delete_unknown_attachment_raises_not_found(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    with pytest.raises(NotFoundError):
        await service.delete_attachment(fixtures.task.id, 9999)


async def test_delete_attachment_scoped_to_its_own_task(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    attachment = await _add(
        service, fixtures.task.id, fixtures.creator.id, "Doc", "https://example.com/doc"
    )

    with pytest.raises(NotFoundError):
        await service.delete_attachment(fixtures.other_task.id, attachment["id"])

    assert len(await service.list_attachments(fixtures.task.id)) == 1


async def test_create_attachment_on_unknown_task_raises_not_found(db_session):
    await seed_task_fixtures(db_session)
    service = build_task_service(db_session)

    with pytest.raises(NotFoundError):
        await _add(service, 9999, 1, "Doc", "https://example.com/doc")


async def test_deleting_a_task_cascades_its_attachments(db_session):
    fixtures = await seed_task_fixtures(db_session)
    service = build_task_service(db_session)
    await _add(service, fixtures.task.id, fixtures.creator.id, "Doc", "https://example.com/doc")

    await db_session.execute(text("DELETE FROM tasks WHERE id = :id"), {"id": fixtures.task.id})

    remaining = await db_session.scalar(select(func.count()).select_from(TaskAttachment))
    assert remaining == 0


@pytest.mark.parametrize(
    "url",
    ["ftp://example.com/file", "file:///etc/passwd", "javascript:alert(1)", "example.com", ""],
)
def test_attachment_create_rejects_non_http_urls(url):
    with pytest.raises(ValidationError):
        TaskAttachmentCreate(name="Doc", url=url)


@pytest.mark.parametrize("url", ["http://example.com/a", "https://example.com/a"])
def test_attachment_create_accepts_http_and_https(url):
    assert TaskAttachmentCreate(name="Doc", url=url).url == url


def test_attachment_create_requires_a_name():
    with pytest.raises(ValidationError):
        TaskAttachmentCreate(name="   ", url="https://example.com/a")
