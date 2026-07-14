"""Клиент GitHub REST API для одностороннего импорта issues."""

from dataclasses import dataclass, field
from typing import Protocol

import httpx

from app.services.exceptions import BadRequestError, NotFoundError, ServiceUnavailableError

_PER_PAGE = 100


@dataclass(frozen=True)
class GitHubIssue:
    number: int
    title: str
    body: str | None
    state: str
    assignee_login: str | None
    labels: list[str] = field(default_factory=list)


class GitHubIssueSource(Protocol):
    async def fetch_issues(self) -> list[GitHubIssue]: ...


def _parse_issue(payload: dict) -> GitHubIssue:
    assignee = payload.get("assignee")
    return GitHubIssue(
        number=payload["number"],
        title=payload["title"],
        body=payload.get("body"),
        state=payload["state"],
        assignee_login=assignee["login"] if assignee else None,
        labels=[label["name"] for label in payload.get("labels", [])],
    )


class GitHubClient:
    """Читает issues репозитория через GitHub REST API.

    transport инжектируется в тестах (httpx.MockTransport), сеть не нужна.
    """

    def __init__(
        self,
        repo: str,
        api_url: str,
        token: str | None = None,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.repo = repo
        self.api_url = api_url.rstrip("/")
        self.token = token
        self.transport = transport

    def _headers(self) -> dict[str, str]:
        headers = {"Accept": "application/vnd.github+json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def _raise_for_status(self, response: httpx.Response) -> None:
        if response.status_code == 404:
            raise NotFoundError(f"GitHub repository {self.repo} not found")
        if response.status_code in (401, 403):
            raise BadRequestError("GitHub rejected the request: check token and rate limits")
        if response.status_code == 429 or response.status_code >= 500:
            raise ServiceUnavailableError("GitHub is temporarily unavailable, try again later")
        if response.status_code >= 400:
            raise BadRequestError(f"GitHub API error: HTTP {response.status_code}")

    async def fetch_issues(self) -> list[GitHubIssue]:
        issues: list[GitHubIssue] = []
        try:
            async with httpx.AsyncClient(transport=self.transport) as client:
                page = 1
                while True:
                    response = await client.get(
                        f"{self.api_url}/repos/{self.repo}/issues",
                        params={"state": "all", "per_page": _PER_PAGE, "page": page},
                        headers=self._headers(),
                    )
                    self._raise_for_status(response)
                    payload = response.json()
                    issues.extend(
                        # Endpoint отдаёт и pull requests — их отличает ключ pull_request.
                        _parse_issue(item)
                        for item in payload
                        if "pull_request" not in item
                    )
                    if len(payload) < _PER_PAGE:
                        return issues
                    page += 1
        except httpx.HTTPError as exc:
            raise ServiceUnavailableError(
                "GitHub is unreachable: check network and proxy settings"
            ) from exc
