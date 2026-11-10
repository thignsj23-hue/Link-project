# LINK — Assistente Android

**Versão de desenvolvimento: 0.2.0**

Aplicativo Android nativo em Kotlin + Jetpack Compose, com backend opcional em Python/FastAPI e integração com a OpenAI Responses API.

## Funcionalidades da 0.2
- Modo local gratuito com respostas básicas, reconhecimento de fala e síntese de voz nativos do Android.
- Memória persistente local em Room/SQLite.
- Personalidade adaptável e preferências de autonomia por categoria.
- Conversação OpenAI pelo backend seguro.
- Seleção e análise de imagens estáticas pelo backend.
- Pesquisa na internet pela ferramenta `web_search`.
- Ativação manual por botão e escuta experimental da palavra “LINK” em serviço de primeiro plano.
- Workflow GitHub Actions para compilar APK debug e empacotar o código-fonte.

## Estrutura
- `LINK-Android/`: aplicativo Android.
- `LINK-Backend/`: backend FastAPI.
- `docs/`: arquitetura, segurança, custos e instruções de compilação.
- `.github/workflows/android-apk.yml`: build em nuvem, sem Android Studio.

## Custos
O modo local não consome créditos da OpenAI. Conversa inteligente, visão e pesquisa web dependem de uma chave e créditos da API OpenAI no backend. Hospedagem, tráfego e uso da API podem gerar custos. A assinatura ChatGPT não inclui créditos de API.

## Documentação
- [Arquitetura e segurança](docs/ARCHITECTURE.md)
- [Compilar e baixar APK sem Android Studio](docs/BUILD.md)
- [Estado real da implementação](docs/STATUS.md)
- [Backend](LINK-Backend/README.md)

O APK só deve ser considerado compilado quando o GitHub Actions terminar com sucesso. Vídeo ao vivo e controle irrestrito de outros aplicativos não fazem parte da versão 0.2.
