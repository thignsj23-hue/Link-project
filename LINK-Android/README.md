# LINK v0.3.0 — IA conectada (piloto)

## O que entrou nesta versão
- Cliente Android nativo Kotlin/Jetpack Compose para conversar com o backend LINK.
- Backend FastAPI em `LINK-Backend/`, integrado à OpenAI Responses API.
- Histórico recente enviado com a conversa (até 20 mensagens).
- Token de acesso entre app e backend; a chave OpenAI permanece exclusivamente no servidor.
- Indicador de conexão, estado de envio e resposta falada via Android TextToSpeech.
- Endpoint de saúde: `GET /health`.

## Arquitetura
Android (HTTPS + token de acesso) → FastAPI → OpenAI Responses API.

## Configuração do backend
Defina no ambiente do servidor:
- `OPENAI_API_KEY`: chave da API OpenAI, nunca no APK ou no repositório.
- `LINK_ACCESS_TOKEN`: token aleatório forte usado pelo app para autenticar no backend.
- `OPENAI_MODEL`: opcional; padrão `gpt-5.5`.

Instalação local:
```bash
cd LINK-Backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export OPENAI_API_KEY="..."
export LINK_ACCESS_TOKEN="..."
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Em produção, publique somente atrás de HTTPS. Gere um token longo e exclusivo e não o compartilhe. O token inserido no APK é uma barreira básica de piloto, não autenticação forte por usuário; antes de distribuir amplamente, implementar identidade individual (ex.: Firebase Auth) e limites de uso por conta.

## Configuração no Android
1. Publique o backend em um serviço HTTPS.
2. Abra LINK > Configurações.
3. Informe a URL base HTTPS (sem `/v1/chat`) e o `LINK_ACCESS_TOKEN`.
4. Envie uma mensagem para validar a resposta real da IA.

A URL do backend e o token são necessários para conexão real. Sem eles, o app não simula uma resposta de IA: informa que ainda não está conectado.

## Limitações explícitas do piloto
- A memória é apenas o histórico recente da conversa enviado pelo app; não há memória persistente entre sessões.
- Seleção de imagens continua disponível, mas análise visual ainda não está implementada.
- Voz usa reconhecimento de fala do Android e TextToSpeech; não é voz em tempo real.
- Não há execução autônoma de ações em outros aplicativos, nem pagamentos ou envio de mensagens.
- Permissões e proteções do Android permanecem obrigatórias.

## Segurança
Nunca coloque `OPENAI_API_KEY` em código Android, BuildConfig, GitHub ou aplicativo. Configure-a como variável secreta do serviço de backend. Use HTTPS. Não exponha detalhes internos ou credenciais nos logs.
