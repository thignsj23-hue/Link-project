# Compilar LINK sem Android Studio

## Compilação automatizada (recomendada)
1. Abra o repositório no GitHub e selecione a branch `feature/link-v0.2-complete`.
2. Entre em **Actions** e abra o workflow **Build LINK Android v0.2**.
3. Aguarde a execução terminar com status **Success**.
4. Abra o resumo da execução e baixe o artefato `LINK-v0.2.0-artifacts`.
5. O ZIP contém `app-debug.apk` e `LINK-Android-v0.2.0-source.zip`.
6. Transfira o APK ao Android e instale. Se solicitado, permita a instalação para o aplicativo que abriu o arquivo.

Não use artefatos de execuções com falha. O APK só existe após o build verde.

## Backend
Consulte [LINK-Backend/README.md](../LINK-Backend/README.md). Configure `OPENAI_API_KEY` e `LINK_ACCESS_TOKEN` no ambiente do servidor. O app precisa somente da URL HTTPS e do token do backend.

## Build local opcional (sem Android Studio)
Instale JDK 17, Android SDK Platform 35, Build Tools 35.0.0 e Gradle 8.9. Na pasta `LINK-Android`, execute `gradle --no-daemon assembleDebug`. O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Requisitos de teste
- Android 8.0/API 26 ou superior.
- Microfone para voz e palavra de ativação.
- Notificações para acompanhar o serviço de primeiro plano.
- Internet, backend HTTPS, token e créditos de API para IA, imagens e pesquisa.
