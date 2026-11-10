import base64
import os
import secrets
from typing import Literal

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, Header, HTTPException
from openai import OpenAI
from pydantic import BaseModel, Field

load_dotenv()
app = FastAPI(title="LINK Backend", version="0.2.0")

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
MODEL = os.getenv("OPENAI_MODEL", "gpt-4.1-mini")
ACCESS_TOKEN = os.getenv("LINK_ACCESS_TOKEN", "").strip()
MAX_IMAGE_BYTES = int(os.getenv("LINK_MAX_IMAGE_BYTES", "10485760"))
client = OpenAI(api_key=OPENAI_API_KEY) if OPENAI_API_KEY else None

BASE_INSTRUCTIONS = (
    "Você é LINK, um assistente pessoal em português do Brasil. "
    "Seja claro, prestativo, honesto e adapte o tom ao contexto. "
    "Não afirme ter executado ações no aparelho sem uma integração real. "
    "Nunca tente contornar permissões do Android. "
    "Envio de mensagens, pagamentos, compras e ações irreversíveis exigem confirmação explícita."
)


class HistoryItem(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=8000)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=8000)
    history: list[HistoryItem] = Field(default_factory=list)
    personality: str = Field(default="adaptive", max_length=40)


class VisionRequest(BaseModel):
    image_base64: str = Field(min_length=20)
    mime_type: Literal["image/jpeg", "image/png", "image/webp"] = "image/jpeg"
    prompt: str = Field(default="Analise esta imagem em português do Brasil.", max_length=2000)


class WebSearchRequest(BaseModel):
    query: str = Field(min_length=2, max_length=2000)


def require_access_token(authorization: str | None = Header(default=None)) -> None:
    if not ACCESS_TOKEN:
        raise HTTPException(status_code=503, detail="LINK_ACCESS_TOKEN não configurado no servidor.")
    expected = f"Bearer {ACCESS_TOKEN}"
    if not authorization or not secrets.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Token de acesso inválido.")


def require_openai() -> OpenAI:
    if client is None:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY não configurada no backend. O modo local do Android continua disponível."
        )
    return client


def output_text(response) -> str:
    text = getattr(response, "output_text", "") or ""
    if not text.strip():
        raise HTTPException(status_code=502, detail="O serviço de IA retornou uma resposta vazia.")
    return text.strip()


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "version": "0.2.0",
        "openai_configured": client is not None,
        "access_token_configured": bool(ACCESS_TOKEN),
    }


@app.post("/v1/chat", dependencies=[Depends(require_access_token)])
def chat(payload: ChatRequest) -> dict[str, str]:
    api = require_openai()
    history = [{"role": item.role, "content": item.content} for item in payload.history[-20:]]
    history.append({"role": "user", "content": payload.message})
    style = (
        "Ajuste o tom e o nível de detalhe ao contexto da conversa."
        if payload.personality == "adaptive"
        else f"Use um estilo {payload.personality}, mantendo clareza e respeito."
    )
    try:
        response = api.responses.create(
            model=MODEL,
            instructions=BASE_INSTRUCTIONS + " " + style,
            input=history,
            max_output_tokens=1200,
        )
        return {"reply": output_text(response)}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Falha ao consultar o serviço de IA.") from exc


@app.post("/v1/vision", dependencies=[Depends(require_access_token)])
def vision(payload: VisionRequest) -> dict[str, str]:
    api = require_openai()
    try:
        raw = base64.b64decode(payload.image_base64, validate=True)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Imagem Base64 inválida.") from exc
    if len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Imagem acima do limite de tamanho.")
    encoded = base64.b64encode(raw).decode("ascii")
    data_url = f"data:{payload.mime_type};base64,{encoded}"
    try:
        response = api.responses.create(
            model=MODEL,
            instructions=BASE_INSTRUCTIONS,
            input=[{
                "role": "user",
                "content": [
                    {"type": "input_text", "text": payload.prompt},
                    {"type": "input_image", "image_url": data_url},
                ],
            }],
            max_output_tokens=1200,
        )
        return {"reply": output_text(response)}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Falha ao analisar a imagem com o serviço de IA.") from exc


@app.post("/v1/web/search", dependencies=[Depends(require_access_token)])
def web_search(payload: WebSearchRequest) -> dict[str, str]:
    api = require_openai()
    try:
        response = api.responses.create(
            model=MODEL,
            tools=[{"type": "web_search"}],
            input="Pesquise na internet e responda em português do Brasil: " + payload.query,
            max_output_tokens=1500,
        )
        return {"reply": output_text(response)}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Falha na pesquisa pela internet.") from exc
