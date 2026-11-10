import os
import re
import secrets
from typing import Literal

from fastapi import Depends, FastAPI, Header, HTTPException
from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AuthenticationError,
    OpenAI,
    OpenAIError,
    PermissionDeniedError,
    RateLimitError,
)
from pydantic import BaseModel, Field

app = FastAPI(title="LINK API", version="0.3.0")
client = OpenAI() if os.getenv("OPENAI_API_KEY") else None
LINK_ACCESS_TOKEN = os.getenv("LINK_ACCESS_TOKEN", "")
MODEL = os.getenv("OPENAI_MODEL", "gpt-5.5")

SYSTEM_INSTRUCTIONS = """Você é LINK, um assistente pessoal em português brasileiro.
Seja claro, prestativo, honesto e objetivo. Não afirme ter executado ações em outros
aplicativos ou dispositivos sem uma integração real e autorização explícita.
Nunca solicite senhas, chaves privadas ou códigos de autenticação. Para mensagens,
pagamentos ou ações externas sensíveis, exija confirmação explícita do usuário.
Respeite as permissões e proteções do Android.
"""

class HistoryItem(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=8000)
    history: list[HistoryItem] = Field(default_factory=list, max_length=20)

class ChatResponse(BaseModel):
    reply: str
    model: str

def require_client_token(authorization: str | None = Header(default=None)):
    if not LINK_ACCESS_TOKEN:
        raise HTTPException(status_code=503, detail="Backend sem LINK_ACCESS_TOKEN configurado.")
    expected = f"Bearer {LINK_ACCESS_TOKEN}"
    if not authorization or not secrets.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Token de acesso inválido.")

def raise_openai_http_error(exc: OpenAIError) -> None:
    """Map OpenAI failures to safe, actionable backend responses."""
    if isinstance(exc, AuthenticationError):
        raise HTTPException(
            status_code=502,
            detail="A OpenAI rejeitou a credencial do servidor. Confira OPENAI_API_KEY no Render.",
        ) from exc
    if isinstance(exc, PermissionDeniedError):
        raise HTTPException(
            status_code=502,
            detail="O projeto OpenAI não tem acesso ao modelo configurado. Confira OPENAI_MODEL e as permissões do projeto.",
        ) from exc
    if isinstance(exc, RateLimitError):
        error_code = None
        body = getattr(exc, "body", None)
        error = body.get("error") if isinstance(body, dict) else None
        code = error.get("code") if isinstance(error, dict) else None
        if isinstance(code, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", code):
            error_code = code
        if error_code == "rate_limit_exceeded":
            detail = "Limite temporário de requisições ou tokens da API atingido (rate_limit_exceeded). Aguarde e tente novamente."
        elif error_code in {
            "insufficient_quota",
            "credit_balance_exhausted",
            "organization_usage_limit_exceeded",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "billing_hard_limit_reached",
        }:
            detail = f"Quota, créditos ou limite de gastos da API atingido ({error_code}). Confira faturamento e limites da organização/projeto."
        elif error_code:
            detail = f"A OpenAI retornou erro 429 ({error_code}). Confira os limites e o faturamento da organização/projeto."
        else:
            detail = "A OpenAI retornou erro 429. Pode ser limite temporário de requisições/tokens ou quota/créditos; confira Usage, Billing e limites da organização/projeto."
        raise HTTPException(
            status_code=429,
            detail=detail,
        ) from exc
    if isinstance(exc, APITimeoutError):
        raise HTTPException(status_code=504, detail="A OpenAI demorou demais para responder.") from exc
    if isinstance(exc, APIConnectionError):
        raise HTTPException(status_code=502, detail="Não foi possível conectar ao serviço OpenAI.") from exc
    if isinstance(exc, APIStatusError) and exc.status_code >= 500:
        raise HTTPException(status_code=502, detail="O serviço OpenAI está indisponível no momento.") from exc
    if isinstance(exc, APIStatusError):
        raise HTTPException(
            status_code=502,
            detail="A OpenAI rejeitou a solicitação. Confira OPENAI_MODEL e os recursos habilitados no projeto.",
        ) from exc
    raise HTTPException(status_code=502, detail="Falha ao consultar o serviço de IA.") from exc

@app.get("/health")
def health():
    return {"status": "ok", "service": "LINK API", "version": "0.3.0", "ai_configured": client is not None}

@app.post("/v1/chat", response_model=ChatResponse, dependencies=[Depends(require_client_token)])
def chat(payload: ChatRequest):
    if client is None:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY não configurada no servidor.")
    conversation = []
    for item in payload.history[-20:]:
        conversation.append({"role": item.role, "content": item.content})
    conversation.append({"role": "user", "content": payload.message})
    try:
        response = client.responses.create(
            model=MODEL,
            instructions=SYSTEM_INSTRUCTIONS,
            input=conversation,
            max_output_tokens=1200,
        )
        answer = response.output_text.strip()
        if not answer:
            raise HTTPException(status_code=502, detail="O modelo retornou uma resposta vazia.")
        return ChatResponse(reply=answer, model=MODEL)
    except HTTPException:
        raise
    except OpenAIError as exc:
        raise_openai_http_error(exc)
    except Exception as exc:
        # Do not expose provider credentials or internal exception details to the client.
        raise HTTPException(status_code=502, detail="Falha ao consultar o serviço de IA. Tente novamente.") from exc
