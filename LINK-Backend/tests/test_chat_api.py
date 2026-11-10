from types import SimpleNamespace
from unittest.mock import Mock

import httpx
import pytest
from fastapi.testclient import TestClient
from openai import AuthenticationError, RateLimitError

from app import main


@pytest.fixture
def api_client(monkeypatch):
    monkeypatch.setattr(main, "ACCESS_TOKEN", "test-link-token")
    return TestClient(main.app)


def post_chat(client, *, token="test-link-token", message="Oi"):
    return client.post(
        "/v1/chat",
        headers={"Authorization": f"Bearer {token}"},
        json={"message": message},
    )


def provider_error(error_type, status_code):
    request = httpx.Request("POST", "https://api.openai.com/v1/responses")
    response = httpx.Response(status_code, request=request, json={"error": {"message": "redacted"}})
    return error_type("provider failure", response=response, body={"error": {"message": "redacted"}})


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
    openai_client.responses.create.side_effect = provider_error(RateLimitError, 429)
    monkeypatch.setattr(main, "client", openai_client)

    response = post_chat(api_client)

    assert response.status_code == 429
    assert "Limite" in response.json()["detail"]


def test_chat_uses_responses_api_and_returns_reply(api_client, monkeypatch):
    openai_client = Mock()
    openai_client.responses.create.return_value = SimpleNamespace(output_text="  Olá!  ")
    monkeypatch.setattr(main, "client", openai_client)
    monkeypatch.setattr(main, "MODEL", "gpt-4.1-mini")

    response = post_chat(api_client, message="Oi, LINK")

    assert response.status_code == 200
    assert response.json() == {"reply": "Olá!"}
    openai_client.responses.create.assert_called_once_with(
        model="gpt-4.1-mini",
        instructions=main.BASE_INSTRUCTIONS + " Ajuste o tom e o nível de detalhe ao contexto da conversa.",
        input=[{"role": "user", "content": "Oi, LINK"}],
        max_output_tokens=1200,
    )
