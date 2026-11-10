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

No Render, configure `LINK-Backend` como **Root Directory** e use `uvicorn app.main:app --host 0.0.0.0 --port $PORT` como comando de inicialização. Defina as variáveis acima nas configurações do serviço e faça um novo deploy após alterá-las. O modelo padrão `gpt-4.1-mini` usa a Responses API; `OPENAI_MODEL` deve indicar um modelo disponível no projeto da chave.

Não envie `OPENAI_API_KEY` ao aplicativo Android. O app recebe somente a URL HTTPS e o token de acesso do backend. O token do backend não é uma chave OpenAI.

## Endpoints
- `GET /health`
- `POST /v1/chat`
- `POST /v1/vision` (imagem estática Base64; limite padrão 10 MB)
- `POST /v1/web/search` (Responses API com ferramenta `web_search`)

As rotas privadas exigem `Authorization: Bearer <LINK_ACCESS_TOKEN>`.

### Diagnóstico de `/v1/chat`

`GET /health` confirma apenas se as variáveis estão presentes, sem revelar seus valores. Para validar a integração após o deploy, envie um `POST /v1/chat` com `Authorization: Bearer <LINK_ACCESS_TOKEN>` e JSON `{"message":"Responda apenas: LINK OK"}`. A resposta esperada é HTTP 200 com `{"reply":"LINK OK"}` (o texto pode variar).

Falhas do provedor têm respostas distintas: chave OpenAI rejeitada ou modelo/permissão incompatível retorna 502 com orientação de configuração; limite/créditos da API retorna 429; tempo excedido retorna 504. Token LINK ausente no servidor retorna 503 e token enviado inválido retorna 401. As mensagens não incluem credenciais nem o corpo bruto do erro do provedor.

## Segurança para produção
Hospede atrás de HTTPS. Use um token longo e exclusivo, controle de acesso por usuário, rate limiting, limites de tamanho e orçamento, rotação de credenciais e logs sem dados sensíveis. Não exponha o servidor de desenvolvimento diretamente à internet. O backend não armazena histórico nesta versão; a memória persistente fica no Room local do Android.

## Custos
O modo local do Android não chama este backend. Chat, visão e pesquisa usam a API OpenAI e podem gerar cobrança por modelo, tokens, imagens e ferramentas. Hospedagem e tráfego também podem custar. A assinatura ChatGPT não fornece créditos de API.

## Testes locais

Instale `pip install -r requirements-dev.txt` e execute `PYTHONPATH=. pytest -q` dentro de `LINK-Backend`. Os testes do chat simulam sucesso, ausência de configuração, token inválido, falha de autenticação OpenAI e limite da API; eles não fazem chamadas pagas ao provedor.
