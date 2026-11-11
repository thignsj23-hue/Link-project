# LINK v0.5.0 — assistente por voz com avatar flutuante

Este projeto é distribuído sob a licença [MIT](../LICENSE).

## O que entrou nesta versão
- Cliente Android nativo Kotlin/Jetpack Compose para conversar com o backend LINK.
- Backend FastAPI em `LINK-Backend/`, integrado à OpenAI Responses API.
- Histórico recente enviado com a conversa (até 20 mensagens).
- Token de acesso entre app e backend; a chave OpenAI permanece exclusivamente no servidor.
- Indicador de conexão, estado de envio e resposta falada via Android TextToSpeech.
- Endpoint de saúde: `GET /health`.
- Detecção automática do idioma de origem em textos longos/imagens entre português, inglês, espanhol, francês, italiano e alemão; tradução no aparelho para esses seis idiomas e leitura de texto em imagens (OCR).
- Modo Link ao Vivo para falar uma frase, traduzir no aparelho e ouvir a tradução.
- Navegação Link Maps que abre a busca no aplicativo de mapas instalado.
- Navegação principal no trilho lateral esquerdo, em celulares e telas maiores.
- Ícone adaptativo próprio e seleção automática da melhor voz local disponível.
- Avatar 2D baseado em imagem selecionada pelo usuário. A conversão local simplifica cores e contornos, sem enviar a imagem para um serviço de geração.
- Personagens e temas visuais selecionáveis e salvos no aparelho.
- Avatar flutuante opcional, com janela própria de conversa por voz, resposta falada, escuta contínua opcional e atalho para abrir o app LINK.
- Entrada “Continuar com ChatGPT” via OAuth/PKCE para solicitar uso elegível da franquia do plano, sem chave de API; tokens são guardados cifrados pelo Android Keystore.
- Pesquisa web acionada por pedidos explícitos, com fontes clicáveis quando disponíveis.
- Atalhos por voz para abrir YouTube, Maps, câmera, navegador, WhatsApp e configurações; busca de músicas abre os resultados do YouTube para o usuário escolher.

## Arquitetura
Tradução/OCR são locais. Conversa pode usar o fluxo oficial “Sign in with ChatGPT” diretamente no Responses API ou, se o usuário escolher, o caminho antigo Android (HTTPS + token) → FastAPI → OpenAI Responses API.

## Recursos locais e custos
- Tradução e OCR são executados no aparelho e não consomem créditos OpenAI.
- Na primeira tradução de um par de idiomas, o ML Kit baixa os modelos necessários (aprox. 30 MB por modelo); a primeira utilização precisa de internet. Depois, a tradução é feita no aparelho.
- O download inicial do modelo de tradução pode usar Wi-Fi ou dados móveis. Os modelos são fornecidos pelo ML Kit; resultados de tradução recebem atribuição ao Google Translate.
- O texto e as imagens processados por tradução/OCR permanecem no aparelho; o ML Kit pode enviar métricas de diagnóstico e utilização conforme a [documentação de privacidade](https://developers.google.com/ml-kit/terms).
- O reconhecimento de voz e a qualidade das vozes dependem dos serviços e pacotes instalados no Android. A resposta inteligente por voz ainda usa a conversa na nuvem.
- Link Maps abre o app de mapas disponível ou a busca web. Cobertura e navegação são fornecidas por esse app.
- O fluxo ChatGPT usa a franquia autorizada e limites de uso da conta ChatGPT; não concede acesso ao GPT personalizado, histórico ou memórias. O uso do plano é separado da API e não implica acesso ilimitado nem elimina os limites da conta.
- O código de uso da franquia segue o fluxo para clientes de código aberto e o repositório inclui a licença MIT. A disponibilidade ainda depende da autorização e das regras aplicáveis à conta ChatGPT.
- O avatar de voz pode usar pesquisa web quando a pessoa pede para pesquisar; a disponibilidade depende do modelo, da conta e das políticas do plano. No modo backend API, essa ferramenta usa a cobrança da API.

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

## Conectar ChatGPT no Android
1. Instale e abra LINK > Configurações.
2. Em “Como o LINK responde”, escolha “Plano ChatGPT” e toque em “Continuar com ChatGPT”.
3. Entre no navegador e autorize o LINK. O app lista os modelos disponíveis para aquela conta.
4. Em ChatGPT > Configurações > Uso, confira e ajuste o limite do LINK.

O consentimento é feito na conta do usuário. O LINK não acessa o GPT personalizado nem as conversas do ChatGPT. Se o uso pelo plano não estiver disponível para a conta, escolha explicitamente “API do LINK” para usar o backend, que pode gerar cobrança separada.

## Configuração do backend opcional no Android
1. Publique o backend em um serviço HTTPS.
2. Abra LINK > Configurações.
3. Informe a URL base HTTPS (sem `/v1/chat`) e o `LINK_ACCESS_TOKEN`.
4. Envie uma mensagem para validar a resposta real da IA.

Esse caminho é opcional quando o plano ChatGPT está conectado. Sem uma das conexões, o app não simula respostas de IA.

## Limitações explícitas do piloto
- A memória é apenas o histórico recente da conversa enviado pelo app; não há memória persistente entre sessões.
- A leitura de imagem reconhece texto impresso em escrita latina; não interpreta objetos nem todos os sistemas de escrita.
- A identificação automática usa o texto extraído e exige conteúdo suficiente; em texto curto ou ambíguo, o LINK usa o idioma de origem escolhido na tela.
- Link ao Vivo traduz uma fala por vez (pressione para cada turno); não é interpretação contínua em streaming.
- A naturalidade da fala depende da voz local instalada. O app seleciona a melhor voz local disponível e ajusta levemente o ritmo.
- O app LINK não é afiliado ao Google. A ação e os resultados de tradução exibem a atribuição exigida pelo ML Kit.
- Ações externas estão limitadas a intents explícitos e apps conhecidos; o LINK não controla integralmente qualquer aplicativo. No YouTube, abre resultados de busca, mas a pessoa escolhe e reproduz a faixa no próprio YouTube.
- A permissão de sobreposição e o microfone precisam ser concedidos pelo usuário. O avatar não recebe permissão para controlar o Android inteiro. A Play Store pode exigir análise adicional do serviço especial em primeiro plano.
- A voz usa os serviços de reconhecimento instalados no Android; o texto reconhecido é enviado ao provedor escolhido (plano ChatGPT ou backend API) para gerar a resposta.
- A transformação local cria uma aparência 2D por posterização e contornos; não usa IA generativa para redesenhar a pessoa.
- A franquia ChatGPT só funciona se a conta/plano conceder o escopo necessário e a integração se enquadrar como cliente de código aberto conforme as regras oficiais.
- Permissões e proteções do Android permanecem obrigatórias.

## Segurança
Nunca coloque `OPENAI_API_KEY` em código Android, BuildConfig, GitHub ou aplicativo. Configure-a como variável secreta do serviço de backend. Use HTTPS. O token OAuth ChatGPT fica cifrado pelo Android Keystore e é enviado diretamente à OpenAI para solicitações autorizadas. Não exponha detalhes internos ou credenciais nos logs.
# Interface no celular

A navegação principal fica no trilho lateral esquerdo em celulares e telas maiores. A tela de tradução permite rolagem vertical e mantém os controles de voz, leitura de imagem e tradução em grupos compactos para evitar rótulos quebrados em aparelhos estreitos. Imagens escolhidas para o avatar passam pelo filtro local de posterização e contorno antes de serem mostradas como ilustração 2D.

## Atualização dentro do app

O botão **Verificar e atualizar** procura a versão estável mais recente nas publicações do GitHub, baixa o APK oficial e abre a confirmação de instalação do Android. A instalação inicial e cada atualização precisam da mesma chave de assinatura. Para publicar uma versão estável, configure os segredos do repositório `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`, depois envie uma tag `vX.Y.Z`. Nunca compartilhe esses segredos em mensagens ou no código. APKs de teste do Actions não são o canal de atualização estável.
