# LINK v0.4.0 — experiência local e IA conectada

## O que entrou nesta versão
- Cliente Android nativo Kotlin/Jetpack Compose para conversar com o backend LINK.
- Backend FastAPI em `LINK-Backend/`, integrado à OpenAI Responses API.
- Histórico recente enviado com a conversa (até 20 mensagens).
- Token de acesso entre app e backend; a chave OpenAI permanece exclusivamente no servidor.
- Indicador de conexão, estado de envio e resposta falada via Android TextToSpeech.
- Endpoint de saúde: `GET /health`.
- Tradução de texto no aparelho para seis idiomas e leitura de texto em imagens (OCR).
- Modo Link ao Vivo para falar uma frase, traduzir no aparelho e ouvir a tradução.
- Navegação Link Maps que abre a busca no aplicativo de mapas instalado.
- Navegação adaptável: barra inferior em celular e trilho lateral em telas largas.
- Ícone adaptativo próprio e seleção automática da melhor voz local disponível.

## Arquitetura
Recursos locais (Android/ML Kit) funcionam sem a API LINK. Conversa inteligente: Android (HTTPS + token) → FastAPI → OpenAI Responses API.

## Recursos locais e custos
- Tradução e OCR são executados no aparelho e não consomem créditos OpenAI.
- Na primeira tradução de um par de idiomas, o ML Kit baixa os modelos necessários (aprox. 30 MB por modelo); a primeira utilização precisa de internet. Depois, a tradução é feita no aparelho.
- O download do modelo de tradução está limitado a Wi-Fi para evitar consumo móvel inesperado. Os modelos são fornecidos pelo ML Kit; resultados de tradução recebem atribuição ao Google Translate.
- O texto e as imagens processados por tradução/OCR permanecem no aparelho; o ML Kit pode enviar métricas de diagnóstico e utilização conforme a [documentação de privacidade](https://developers.google.com/ml-kit/terms).
- O reconhecimento de voz e a qualidade das vozes dependem dos serviços e pacotes instalados no Android. A resposta inteligente por voz ainda usa a conversa na nuvem.
- Link Maps abre o app de mapas disponível ou a busca web. Cobertura e navegação são fornecidas por esse app.

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
- A leitura de imagem reconhece texto impresso em escrita latina; não interpreta objetos nem todos os sistemas de escrita.
- Link ao Vivo traduz uma fala por vez (pressione para cada turno); não é interpretação contínua em streaming.
- A naturalidade da fala depende da voz local instalada. O app seleciona a melhor voz local disponível e ajusta levemente o ritmo.
- O app LINK não é afiliado ao Google. A ação e os resultados de tradução exibem a atribuição exigida pelo ML Kit.
- Não há execução autônoma de ações em outros aplicativos, nem pagamentos ou envio de mensagens.
- Permissões e proteções do Android permanecem obrigatórias.

## Segurança
Nunca coloque `OPENAI_API_KEY` em código Android, BuildConfig, GitHub ou aplicativo. Configure-a como variável secreta do serviço de backend. Use HTTPS. Não exponha detalhes internos ou credenciais nos logs.

