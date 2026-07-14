"""Тесты OpenRouter-клиента на httpx.MockTransport — без сети."""

import json

import httpx
import pytest

from app.integrations.openrouter_client import OpenRouterClient, SuggestionCandidate
from app.services.exceptions import BadRequestError, ServiceUnavailableError

API_URL = "https://or.test/api/v1"

LABELS = [SuggestionCandidate(id=1, name="Backend"), SuggestionCandidate(id=2, name="Frontend")]
SKILLS = [SuggestionCandidate(id=10, name="Python"), SuggestionCandidate(id=11, name="React")]


def _completion_response(content: str) -> dict:
    return {"choices": [{"message": {"role": "assistant", "content": content}}]}


def _client(handler) -> OpenRouterClient:
    return OpenRouterClient(
        api_key="secret-key",
        model="test/model",
        api_url=API_URL,
        transport=httpx.MockTransport(handler),
    )


async def _suggest(client: OpenRouterClient):
    return await client.suggest_fields(
        title="Fix login bug",
        description="Users cannot log in",
        labels=LABELS,
        skills=SKILLS,
    )


async def test_suggest_fields_parses_response():
    content = json.dumps(
        {
            "difficulty": 4,
            "estimated_days": 7,
            "label_ids": [1],
            "skills": [{"skill_id": 10, "exp_reward": 120}],
        }
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response(content))

    raw = await _suggest(_client(handler))

    assert raw.difficulty == 4
    assert raw.estimated_days == 7
    assert raw.label_ids == [1]
    assert raw.skills == [(10, 120)]


async def test_suggest_fields_strips_code_fence():
    content = '```json\n{"difficulty": 2, "label_ids": [2], "skills": []}\n```'

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response(content))

    raw = await _suggest(_client(handler))

    assert raw.difficulty == 2
    assert raw.label_ids == [2]
    assert raw.skills == []


async def test_suggest_fields_sends_auth_model_and_candidates():
    seen: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["path"] = request.url.path
        seen["auth"] = request.headers.get("Authorization")
        seen["body"] = json.loads(request.content)
        return httpx.Response(
            200,
            json=_completion_response('{"difficulty": 3, "label_ids": [], "skills": []}'),
        )

    await _suggest(_client(handler))

    assert seen["path"] == "/api/v1/chat/completions"
    assert seen["auth"] == "Bearer secret-key"
    body = seen["body"]
    assert body["model"] == "test/model"
    assert body["response_format"] == {"type": "json_object"}
    assert body["messages"][0]["role"] == "system"
    user_payload = json.loads(body["messages"][1]["content"])
    assert user_payload["title"] == "Fix login bug"
    assert user_payload["description"] == "Users cannot log in"
    assert user_payload["label_candidates"] == [
        {"id": 1, "name": "Backend"},
        {"id": 2, "name": "Frontend"},
    ]
    assert user_payload["skill_candidates"] == [
        {"id": 10, "name": "Python"},
        {"id": 11, "name": "React"},
    ]


async def test_suggest_fields_ignores_non_integer_values():
    content = json.dumps(
        {
            "difficulty": "hard",
            "estimated_days": "a week",
            "label_ids": [1, "two", None],
            "skills": [
                {"skill_id": 10, "exp_reward": 100},
                {"skill_id": "React", "exp_reward": 50},
                {"skill_id": 11},
                "not-a-dict",
            ],
        }
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response(content))

    raw = await _suggest(_client(handler))

    assert raw.difficulty is None
    assert raw.estimated_days is None
    assert raw.label_ids == [1]
    assert raw.skills == [(10, 100)]


async def test_suggest_fields_ignores_boolean_values():
    content = json.dumps(
        {
            "difficulty": True,
            "estimated_days": False,
            "label_ids": [True, 1],
            "skills": [
                {"skill_id": True, "exp_reward": 100},
                {"skill_id": 10, "exp_reward": False},
            ],
        }
    )

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response(content))

    raw = await _suggest(_client(handler))

    assert raw.difficulty is None
    assert raw.estimated_days is None
    assert raw.label_ids == [1]
    assert raw.skills == []


@pytest.mark.parametrize("status_code", [401, 403, 404])
async def test_suggest_fields_client_errors_raise_bad_request(status_code):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status_code, json={"error": "denied"})

    with pytest.raises(BadRequestError):
        await _suggest(_client(handler))


@pytest.mark.parametrize("status_code", [429, 500])
async def test_suggest_fields_temporary_errors_raise_service_unavailable(status_code):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status_code, json={"error": "unavailable"})

    with pytest.raises(ServiceUnavailableError):
        await _suggest(_client(handler))


async def test_suggest_fields_connection_error_raises_service_unavailable():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("All connection attempts failed", request=request)

    with pytest.raises(ServiceUnavailableError):
        await _suggest(_client(handler))


async def test_suggest_fields_malformed_json_content_raises_bad_request():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response("difficulty is about 3 or so"))

    with pytest.raises(BadRequestError):
        await _suggest(_client(handler))


async def test_suggest_fields_unexpected_response_shape_raises_bad_request():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"unexpected": "shape"})

    with pytest.raises(BadRequestError):
        await _suggest(_client(handler))


@pytest.mark.parametrize("content", [None, [{"type": "text", "text": "{}"}]])
async def test_suggest_fields_non_string_content_raises_bad_request(content):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response(content))

    with pytest.raises(BadRequestError, match="unexpected response shape"):
        await _suggest(_client(handler))


async def test_suggest_fields_non_object_content_raises_bad_request():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=_completion_response('["not", "an", "object"]'))

    with pytest.raises(BadRequestError):
        await _suggest(_client(handler))
