# LINK Android v0.1.0

Base inicial do aplicativo Android nativo em Kotlin e Jetpack Compose.

Inclui interface de conversa, histórico em memória durante a sessão, reconhecimento de fala pt-BR, resposta falada com Android TextToSpeech, seleção de imagem pela galeria e preferências iniciais de personalidade/autonomia.

Ainda não inclui backend, conexão com modelo de IA, memória persistente, autenticação, análise de imagens ou integração autônoma com outros aplicativos.

## Compilação
Abra esta pasta no Android Studio, instale Android SDK 35, sincronize o Gradle e execute em dispositivo Android 8.0 ou superior.

## Segurança
Chaves de API devem permanecer no backend, nunca embutidas no APK. Solicite permissões apenas quando necessárias e exija confirmação para ações externas sensíveis.
