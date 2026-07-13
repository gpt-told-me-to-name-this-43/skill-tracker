"""Тесты GitHub-клиента на httpx.MockTransport — без сети."""

import json

import httpx
import pytest

from app.integrations.github_client import GitHubClient
from app.services.exceptions import BadRequestError, NotFoundError

REPO = "acme/widgets"
API_URL = "https://gh.test"


def _issue(number: int, **overrides) -> dict:
    payload = {
        "number": number,
        "title": f"Issue {number}",
        "body": f"Body {number}",
        "state": "open",
        "assignee": None,
        "labels": [],
    }
    payload.update(overrides)
    return payload


def _client(handler) -> GitHubClient:
    return GitHubClient(
        repo=REPO,
        api_url=API_URL,
        transport=httpx.MockTransport(handler),
    )


async def test_fetch_issues_parses_fields():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json=[
                _issue(
                    7,
                    title="Fix login",
                    body="Steps to reproduce",
                    state="closed",
                    assignee={"login": "octocat"},
                    labels=[{"name": "bug"}, {"name": "Backend"}],
                ),
                _issue(8, body=None),
            ],
        )

    issues = await _client(handler).fetch_issues()

    assert len(issues) == 2
    first = issues[0]
    assert first.number == 7
    assert first.title == "Fix login"
    assert first.body == "Steps to reproduce"
    assert first.state == "closed"
    assert first.assignee_login == "octocat"
    assert first.labels == ["bug", "Backend"]
    assert issues[1].assignee_login is None
    assert issues[1].body is None


async def test_fetch_issues_filters_pull_requests():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json=[
                _issue(1),
                _issue(2, pull_request={"url": "https://gh.test/pr/2"}),
            ],
        )

    issues = await _client(handler).fetch_issues()

    assert [issue.number for issue in issues] == [1]


async def test_fetch_issues_paginates_until_short_page():
    pages: list[dict] = []

    def handler(request: httpx.Request) -> httpx.Response:
        params = dict(request.url.params)
        pages.append(params)
        page = int(params["page"])
        if page == 1:
            return httpx.Response(200, json=[_issue(n) for n in range(1, 101)])
        return httpx.Response(200, json=[_issue(101)])

    issues = await _client(handler).fetch_issues()

    assert len(issues) == 101
    assert [params["page"] for params in pages] == ["1", "2"]
    assert all(params["state"] == "all" for params in pages)
    assert all(params["per_page"] == "100" for params in pages)


async def test_fetch_issues_sends_token_and_hits_repo_url():
    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["auth"] = request.headers.get("Authorization")
        seen["accept"] = request.headers.get("Accept")
        return httpx.Response(200, json=[])

    client = GitHubClient(
        repo=REPO,
        api_url=API_URL,
        token="secret-token",
        transport=httpx.MockTransport(handler),
    )
    await client.fetch_issues()

    assert seen["path"] == f"/repos/{REPO}/issues"
    assert seen["auth"] == "Bearer secret-token"
    assert seen["accept"] == "application/vnd.github+json"


async def test_fetch_issues_without_token_sends_no_auth_header():
    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers.get("Authorization")
        return httpx.Response(200, json=[])

    await _client(handler).fetch_issues()

    assert seen["auth"] is None


async def test_fetch_issues_404_raises_not_found():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, json={"message": "Not Found"})

    with pytest.raises(NotFoundError):
        await _client(handler).fetch_issues()


@pytest.mark.parametrize("status_code", [401, 403])
async def test_fetch_issues_auth_and_rate_limit_raise_bad_request(status_code):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status_code, json={"message": "denied"})

    with pytest.raises(BadRequestError):
        await _client(handler).fetch_issues()


async def test_fetch_issues_server_error_raises_bad_request():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, content=json.dumps({"message": "boom"}))

    with pytest.raises(BadRequestError):
        await _client(handler).fetch_issues()
