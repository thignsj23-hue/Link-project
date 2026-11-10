# Arquitetura LINK 0.2.0

## Visão geral

```
Android (Kotlin / Jetpack Compose)
  ├─ UI e configurações
  ├─ VOICE: reconhecimento e TTS nativos
  ├─ MEMORY: Room / SQLite local
  ├─ WAKE WORD: serviço de primeiro plano experimental
  └─ NETWORK: HTTPS + token de acesso
         |
         v
Python / FastAPI
  ├─ POST /v1/chat       -> OpenAI Responses API
  ├─ POST /v1/vision     -> análise de imagem
  └─ POST /v1/web/search -> ferramenta web_search
         |
         v
OpenAI API (chave somente no servidor)
```

## Modo local gratuito
Com a URL do backend vazia ou sem credenciais de acesso, o app mantém respostas básicas locais, usa reconhecimento de fala e TTS do Android e grava o histórico em Room. O modo local não executa um LLM; a qualidade e a disponibilidade do reconhecimento offline dependem do aparelho e do pacote de idioma.

## Recursos com custo variável
- Conversa inteligente: chamadas ao modelo OpenAI configurado no backend.
- Análise visual: envio de imagem estática e processamento pelo modelo de visão.
- Pesquisa web: chamada Responses API com ferramenta `web_search`.
- Hospedagem, rede e armazenamento do backend.

O custo depende do modelo, tokens, imagens e uso da ferramenta. ChatGPT Plus/Pro não fornece créditos de API.

## Segurança
- `OPENAI_API_KEY` é variável de ambiente exclusiva do backend. Nunca colocá-la no APK, em BuildConfig ou em arquivos versionados.
- `LINK_ACCESS_TOKEN` protege as rotas do backend; o app solicita esse token na tela de configurações e o mantém apenas em memória durante a sessão.
- Use HTTPS, token forte, rate limiting, controle de acesso por usuário e limites de requisição ao publicar.
- A memória persistente padrão é local; o backend não armazena conversas nesta versão.
- Ações sensíveis (mensagens, pagamentos, compras e ações irreversíveis) sempre exigem confirmação. Os níveis de autonomia são preferências, não autorização para ignorar permissões Android.

## Memória
Room persiste as últimas 100 mensagens no banco local `link-memory.db`. O usuário pode apagar o histórico pela tela de configurações. O backend não recebe a memória completa nem persiste dados de conversa.

## Ativação por voz
- Botão “Falar”: reconhecimento de fala pontual pelo Android.
- Botão “Ativar LINK”: inicia serviço de primeiro plano com notificação e tenta reconhecer “LINK” em pt-BR, preferindo o pacote offline quando disponível.
- Ao detectar a palavra, a notificação é atualizada; o usuário toca nela para abrir o app. Não é ativação hands-free garantida.
- Android pode limitar o serviço por bateria, fabricante, permissões e política de microfone em segundo plano.

## Limitações da 0.2
- Fotos estáticas selecionadas pelo usuário; câmera ao vivo e vídeo contínuo não implementados.
- Sem controle irrestrito de outros apps, pagamentos ou envio de mensagens.
- Autonomia por categoria é persistida, mas ainda não governa um executor de ações.
