# LINK Backend 0.2.0

Backend opcional em FastAPI para conectar o aplicativo Android à OpenAI.

## Configuração
1. Use Python 3.12.
2. Instale: `pip install -r requirements.txt`.
3. Copie `.env.example` para `.env`.
4. Configure no ambiente do servidor:
   - `OPENAI_API_KEY`: chave da API OpenAI.
   - `LINK_ACCESS_TOKEN`: token aleatório forte para proteger as rotas do LINK.
   - `OPENAI_MODEL`: opcional; padrão `gpt-4.1-mini`.
5. Execute: `uvicorn app.main:app --host 0.0.0.0 --port 8000`.

Não envie `OPENAI_API_KEY` ao aplicativo Android. O app recebe somente a URL HTTPS e o token de acesso do backend. O token do backend não é uma chave OpenAI.

## Endpoints
- `GET /health`
- `POST /v1/chat`
- `POST /v1/vision` (imagem estática Base64; limite padrão 10 MB)
- `POST /v1/web/search` (Responses API com ferramenta `web_search`)

As rotas privadas exigem `Authorization: Bearer <LINK_ACCESS_TOKEN>`.

## Segurança para produção
Hospede atrás de HTTPS. Use um token longo e exclusivo, controle de acesso por usuário, rate limiting, limites de tamanho e orçamento, rotação de credenciais e logs sem dados sensíveis. Não exponha o servidor de desenvolvimento diretamente à internet. O backend não armazena histórico nesta versão; a memória persistente fica no Room local do Android.

## Custos
O modo local do Android não chama este backend. Chat, visão e pesquisa usam a API OpenAI e podem gerar cobrança por modelo, tokens, imagens e ferramentas. Hospedagem e tráfego também podem custar. A assinatura ChatGPT não fornece créditos de API.
