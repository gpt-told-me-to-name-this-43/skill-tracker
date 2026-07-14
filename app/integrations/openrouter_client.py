"""Клиент OpenRouter для ML-подсказок полей задачи (labels, skills, difficulty)."""

import json
from dataclasses import dataclass, field
from typing import Protocol

import httpx

from app.services.exceptions import BadRequestError, ServiceUnavailableError

_TIMEOUT_SECONDS = 30.0

_SYSTEM_PROMPT = (
    "You are an assistant for a task tracker. Given a task title and description, "
    "suggest a difficulty (integer 1-5), a realistic estimate of days needed to "
    "complete the task (integer 1-365), matching labels and relevant skills with "
    "an XP reward (integer 1-1000) proportional to the difficulty. "
    "Pick labels and skills ONLY from the provided candidates, by their ids. "
    "Respond with strict JSON, no prose, in the shape: "
    '{"difficulty": 3, "estimated_days": 7, "label_ids": [1, 2], '
    '"skills": [{"skill_id": 1, "exp_reward": 100}]}'
)


@dataclass(frozen=True)
class SuggestionCandidate:
    """Кандидат (label или skill) для выбора моделью."""

    id: int
    name: str


@dataclass(frozen=True)
class RawTaskSuggestion:
    """Непроверенный ответ модели; валидируется в TaskSuggestionService."""

    difficulty: int | None
    estimated_days: int | None = None
    label_ids: list[int] = field(default_factory=list)
    skills: list[tuple[int, int]] = field(default_factory=list)  # (skill_id, exp_reward)


class TaskSuggestionSource(Protocol):
    async def suggest_fields(
        self,
        title: str,
        description: str | None,
        labels: list[SuggestionCandidate],
        skills: list[SuggestionCandidate],
    ) -> RawTaskSuggestion: ...


def _strip_code_fence(content: str) -> str:
    content = content.strip()
    if content.startswith("```"):
        content = content.split("\n", 1)[1] if "\n" in content else ""
        content = content.rsplit("```", 1)[0]
    return content.strip()


def _parse_suggestion(content: str) -> RawTaskSuggestion:
    try:
        payload = json.loads(_strip_code_fence(content))
    except json.JSONDecodeError as exc:
        raise BadRequestError("ML provider returned malformed JSON") from exc

    if not isinstance(payload, dict):
        raise BadRequestError("ML provider returned unexpected response shape")

    difficulty = payload.get("difficulty")
    if isinstance(difficulty, bool) or not isinstance(difficulty, int):
        difficulty = None

    estimated_days = payload.get("estimated_days")
    if isinstance(estimated_days, bool) or not isinstance(estimated_days, int):
        estimated_days = None

    label_ids = [
        item
        for item in payload.get("label_ids") or []
        if isinstance(item, int) and not isinstance(item, bool)
    ]

    skills: list[tuple[int, int]] = []
    for item in payload.get("skills") or []:
        if not isinstance(item, dict):
            continue
        skill_id = item.get("skill_id")
        exp_reward = item.get("exp_reward")
        if (
            isinstance(skill_id, int)
            and not isinstance(skill_id, bool)
            and isinstance(exp_reward, int)
            and not isinstance(exp_reward, bool)
        ):
            skills.append((skill_id, exp_reward))

    return RawTaskSuggestion(
        difficulty=difficulty,
        estimated_days=estimated_days,
        label_ids=label_ids,
        skills=skills,
    )


class OpenRouterClient:
    """Запрашивает подсказки полей задачи через OpenRouter chat/completions.

    transport инжектируется в тестах (httpx.MockTransport), сеть не нужна.
    """

    def __init__(
        self,
        api_key: str,
        model: str,
        api_url: str,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.api_key = api_key
        self.model = model
        self.api_url = api_url.rstrip("/")
        self.transport = transport

    def _raise_for_status(self, response: httpx.Response) -> None:
        if response.status_code in (401, 403):
            raise BadRequestError("ML provider rejected the request: check the API key")
        if response.status_code == 429 or response.status_code >= 500:
            raise ServiceUnavailableError("ML provider is temporarily unavailable, try again later")
        if response.status_code >= 400:
            raise BadRequestError(f"ML provider rejected the request: HTTP {response.status_code}")

    async def suggest_fields(
        self,
        title: str,
        description: str | None,
        labels: list[SuggestionCandidate],
        skills: list[SuggestionCandidate],
    ) -> RawTaskSuggestion:
        user_message = json.dumps(
            {
                "title": title,
                "description": description or "",
                "label_candidates": [{"id": item.id, "name": item.name} for item in labels],
                "skill_candidates": [{"id": item.id, "name": item.name} for item in skills],
            },
            ensure_ascii=False,
        )
        try:
            async with httpx.AsyncClient(
                transport=self.transport, timeout=_TIMEOUT_SECONDS
            ) as client:
                response = await client.post(
                    f"{self.api_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json={
                        "model": self.model,
                        "messages": [
                            {"role": "system", "content": _SYSTEM_PROMPT},
                            {"role": "user", "content": user_message},
                        ],
                        "response_format": {"type": "json_object"},
                    },
                )
        except httpx.HTTPError as exc:
            raise ServiceUnavailableError(
                "ML provider is unreachable: check network and proxy settings"
            ) from exc
        self._raise_for_status(response)

        try:
            content = response.json()["choices"][0]["message"]["content"]
        except (json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
            raise BadRequestError("ML provider returned unexpected response shape") from exc

        if not isinstance(content, str):
            raise BadRequestError("ML provider returned unexpected response shape")

        return _parse_suggestion(content)
