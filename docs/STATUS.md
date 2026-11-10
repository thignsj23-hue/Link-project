# Estado da implementação — LINK 0.2.0

| Recurso | Estado |
|---|---|
| App Kotlin + Jetpack Compose | Compilação Android debug concluída com sucesso no GitHub Actions |
| APK debug | Gerado em 10/11/2026; disponível no artefato LINK-v0.2.0-artifacts |
| Teste de backend | Sintaxe validada e 3 testes pytest aprovados |
| Conversa básica local | Implementada no código |
| Reconhecimento de fala nativo | Implementado no código; depende do aparelho |
| Síntese de voz nativa | Implementada no código; depende do aparelho |
| Memória local Room/SQLite | Implementada no código |
| Personalidade adaptável | Preferência persistida e enviada ao backend |
| Autonomia por categoria | Preferências persistidas; ainda não controlam executor |
| Backend FastAPI | Implementado no código |
| OpenAI Responses API | Requer chave e créditos no servidor |
| Análise de imagem estática | Requer backend e API |
| Pesquisa web | Requer backend, API e ferramenta web_search |
| Palavra LINK | Serviço experimental; requer permissão, notificação e pacote de reconhecimento |
| Vídeo ao vivo | Não implementado |
| Controle geral de apps | Não implementado |
| Confirmação de ações sensíveis | Regra documentada; sem executor de ações nesta versão |
| Teste em aparelho físico | Pendente |
 
Build validado: workflow [42813837152](https://github.com/thignsj23-hue/Link-project/actions/runs/42813837152), commit `f3f4fba7ec929caa59dcd68c0337899325f08ded`. O artefato contém `app-debug.apk` e `LINK-Android-v0.2.0-source.zip`.
