from types import SimpleNamespace
from unittest.mock import Mock

import httpx
import pytest
from fastapi.testclient import TestClient
from openai import AuthenticationError, RateLimitError

from app import main


@pytest.fixture
def api_client(monkeypatch):
    monkeypatch.setattr(main, "LINK_ACCESS_TOKEN", "test-link-token")
    return TestClient(main.app)


def post_chat(client, *, token="test-link-token", message="Oi"):
    return client.post(
        "/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={"message": message},
    )


def provider_error(error_type, status_code, *, code=None):
    request = httpx.Request("POST", "https://api.openai.com/v1/responses")
    error = {"message": "redacted", "code": code}
    response = httpx.Response(status_code, request=request, json={"error": error})
    return error_type("provider failure", response=response, body={"error": error})


def test_chat_reports_missing_openai_configuration(api_client, monkeypatch):
    monkeypatch.setattr(main, "client", None)

    response = post_chat(api_client)

    assert response.status_code == 503
    assert "OPENAI_API_KEY" in response.json()["detail"]


def test_chat_rejects_invalid_access_token(api_client, monkeypatch):
    openai_client = Mock()
    monkeypatch.setattr(main, "client", openai_client)

    response = post_chat(api_client, token="wrong-token")

    assert response.status_code == 401
    openai_client.responses.create.assert_not_called()


def test_chat_maps_openai_authentication_failure(api_client, monkeypatch):
    openai_client = Mock()
    openai_client.responses.create.side_effect = provider_error(AuthenticationError, 401)
    monkeypatch.setattr(main, "client", openai_client)

    response = post_chat(api_client)

    assert response.status_code == 502
    assert "OPENAI_API_KEY" in response.json()["detail"]


def test_chat_maps_openai_rate_limit(api_client, monkeypatch):
    openai_client = Mock()
    openai_client.responses.create.side_effect = provider_error(
        RateLimitError, 429, code="insufficient_quota"
    )
    monkeypatch.setattr(main, "client", openai_client)

    response = post_chat(api_client)

    assert response.status_code == 429
    assert "insufficient_quota" in response.json()["detail"]


def test_chat_distinguishes_temporary_rate_limit(api_client, monkeypatch):
    openai_client = Mock()
    openai_client.responses.create.side_effect = provider_error(
        RateLimitError, 429, code="rate_limit_exceeded"
    )
    monkeypatch.setattr(main, "client", openai_client)

    response = post_chat(api_client)

    assert response.status_code == 429
    assert "temporário" in response.json()["detail"]
    assert "rate_limit_exceeded" in response.json()["detail"]


def test_chat_uses_responses_api_and_returns_reply(api_client, monkeypatch):
    openai_client = Mock()
    openai_client.responses.create.return_value = SimpleNamespace(output_text="  Olá!  ")
    monkeypatch.setattr(main, "client", openai_client)
    monkeypatch.setattr(main, "MODEL", "gpt-5.5")

    response = post_chat(api_client, message="Oi, LINK")

    assert response.status_code == 200
    assert response.json() == {"reply": "Olá!", "model": "gpt-5.5"}
    openai_client.responses.create.assert_called_once_with(
        model="gpt-5.5",
        instructions=main.SYSTEM_INSTRUCTIONS,
        input=[{"role": "user", "content": "Oi, LINK"}],
        max_output_tokens=1200,
    )


def test_chat_enables_web_search_and_returns_source_links(api_client, monkeypatch):
    annotation = SimpleNamespace(url="https://example.com/source", title="Fonte")
    content = SimpleNamespace(annotations=[annotation])
    output = SimpleNamespace(content=[content])
    openai_client = Mock()
    openai_client.responses.create.return_value = SimpleNamespace(
        output_text="Resposta pesquisada", output=[output]
    )
    monkeypatch.setattr(main, "client", openai_client)

    response = api_client.post(
        "/v1/chat",
        headers={"Authorization": "Bearer test-link-token"},
        json={"message": "Pesquise isso", "web_search": True},
    )

    assert response.status_code == 200
    assert "Resposta pesquisada" in response.json()["reply"]
    assert "https://example.com/source" in response.json()["reply"]
    kwargs = openai_client.responses.create.call_args.kwargs
    assert kwargs["tools"] == [{"type": "web_search"}]
    assert kwargs["tool_choice"] == "required"
