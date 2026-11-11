package com.link.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Night = Color(0xFF080D18)
private val Panel = Color(0xFF111C2D)
private val Cyan = Color(0xFF55D6E8)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { LinkApp(onSpeak = { text, language -> speak(text, language) }) }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) setVoiceFor("pt")
    }
    private fun setVoiceFor(language: String) {
        val engine = tts ?: return
        val locale = if (language.equals("pt", ignoreCase = true) || language.startsWith("pt-", ignoreCase = true)) {
            Locale.forLanguageTag("pt-BR")
        } else Locale.forLanguageTag(language)
        val available = engine.voices.orEmpty().filter { it.locale.language == locale.language }
        val localVoices = available.filter { !it.isNetworkConnectionRequired }
        val voice: Voice? = localVoices
            .filter { it.locale.country.equals(locale.country, ignoreCase = true) }
            .maxByOrNull { it.quality }
            ?: localVoices.maxByOrNull { it.quality }
        if (voice != null) engine.voice = voice else engine.language = locale
        engine.setSpeechRate(0.94f)
        engine.setPitch(0.96f)
    }
    private fun speak(text: String, language: String) {
        setVoiceFor(language)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "link_reply")
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

data class ChatLine(val fromLink: Boolean, val text: String)
fun asksForWebSearch(text: String): Boolean = Regex("\\b(pesquisa|pesquise|pesquisar|pesquisando|procure|busque|buscar|internet|online|fontes atuais|previsão(?: do tempo)?|clima|temperatura|vai chover|chuva|weather|forecast|hoje|agora|atual|atualmente|notícias|últimas notícias|cotação|preço atual)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
private enum class LinkPage(val label: String) { CHAT("Conversa"), TRANSLATE("Traduzir"), LIVE("Link ao vivo"), MAPS("Maps"), VOICE("Link Voice") }
private data class LanguageOption(val name: String, val id: String, val locale: String)
private val languageOptions = listOf(
    LanguageOption("Português", "pt", "pt-BR"), LanguageOption("English", "en", "en-US"),
    LanguageOption("Español", "es", "es-ES"), LanguageOption("Français", "fr", "fr-FR"),
    LanguageOption("Italiano", "it", "it-IT"), LanguageOption("Deutsch", "de", "de-DE")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkApp(onSpeak: (String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("link_settings", Context.MODE_PRIVATE) }
    fun refreshAvatarOverlay() {
        if (prefs.getBoolean("avatar_overlay_enabled", false) && Settings.canDrawOverlays(context)) {
            ContextCompat.startForegroundService(context, Intent(context, AvatarOverlayService::class.java))
        }
    }
    var page by remember { mutableStateOf(LinkPage.CHAT) }
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var listenForTranslation by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf("Adaptável") }
    var autonomy by remember { mutableStateOf("Assistido") }
    var backendUrl by remember { mutableStateOf(prefs.getString("backend_url", "") ?: "") }
    var accessToken by remember { mutableStateOf(TokenCipher.read(context).orEmpty()) }
    var aiProvider by remember { mutableStateOf(prefs.getString("ai_provider", "chatgpt_plan") ?: "chatgpt_plan") }
    var chatGptEmail by remember { mutableStateOf(ChatGptPlanClient.signedInEmail(context).orEmpty()) }
    var chatGptModels by remember { mutableStateOf(emptyList<ChatGptModel>()) }
    var selectedChatGptModel by remember { mutableStateOf(prefs.getString("chatgpt_model", "") ?: "") }
    var showModelPicker by remember { mutableStateOf(false) }
    var avatarFilePath by remember { mutableStateOf(prefs.getString("avatar_2d_path", "") ?: "") }
    var usePhotoAvatar by remember { mutableStateOf(prefs.getBoolean("avatar_use_photo", false)) }
    var avatarCharacter by remember { mutableStateOf(prefs.getString("avatar_character", "Raposa") ?: "Raposa") }
    var avatarTheme by remember { mutableStateOf(prefs.getString("avatar_theme", "Aurora") ?: "Aurora") }
    var status by remember { mutableStateOf("Modo local disponível") }
    var updateStatus by remember { mutableStateOf("Atualizações oficiais serão verificadas dentro do app.") }
    var autoSendVoice by remember { mutableStateOf(false) }
    var translationInput by remember { mutableStateOf("") }
    var translationOutput by remember { mutableStateOf("") }
    var autoDetectLanguage by remember { mutableStateOf(true) }
    var sourceLanguage by remember { mutableStateOf(languageOptions[0]) }
    var targetLanguage by remember { mutableStateOf(languageOptions[1]) }
    var translating by remember { mutableStateOf(false) }
    var selectSource by remember { mutableStateOf(false) }
    var selectTarget by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatLine>() }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            if (listenForTranslation) {
                translationInput = spoken
                scope.launch {
                    translating = true
                    try {
                        val detected = if (autoDetectLanguage) LocalTranslation.detectLanguage(spoken) else null
                        val detectedOption = languageOptions.firstOrNull { it.id == detected }
                        val sourceId = detectedOption?.id ?: sourceLanguage.id
                        if (detectedOption != null) sourceLanguage = detectedOption
                        translationOutput = LocalTranslation.translate(spoken, sourceId, targetLanguage.id)
                        status = if (detectedOption != null) "Idioma detectado: ${detectedOption.name} • traduzido no aparelho" else "Idioma não identificado com segurança; usado ${sourceLanguage.name}"
                        onSpeak(translationOutput, targetLanguage.id)
                    } catch (e: Exception) { status = e.message ?: "Não foi possível traduzir" }
                    finally { translating = false; listenForTranslation = false }
                }
            } else if (page == LinkPage.VOICE) { draft = spoken; autoSendVoice = true } else draft = spoken
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            page = LinkPage.TRANSLATE
            scope.launch {
                translating = true
                try {
                    val extracted = LocalTranslation.readImage(context, uri)
                    translationInput = extracted
                    val detected = if (autoDetectLanguage) LocalTranslation.detectLanguage(extracted) else null
                    val detectedOption = languageOptions.firstOrNull { it.id == detected }
                    val sourceId = detectedOption?.id ?: sourceLanguage.id
                    if (detectedOption != null) sourceLanguage = detectedOption
                    translationOutput = LocalTranslation.translate(extracted, sourceId, targetLanguage.id)
                    status = if (detectedOption != null) "Imagem • idioma detectado: ${detectedOption.name}" else "Texto da imagem • usado ${sourceLanguage.name}"
                } catch (e: Exception) { status = e.message ?: "Não foi possível ler essa imagem" }
                finally { translating = false }
            }
        }
    }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            status = "Criando seu avatar 2D..."
            runCatching { withContext(Dispatchers.IO) { AvatarStyle.stylizeAndSave(context, uri) } }
                .onSuccess { file ->
                    avatarFilePath = file.absolutePath
                    usePhotoAvatar = true
                    prefs.edit().putString("avatar_2d_path", file.absolutePath).putBoolean("avatar_use_photo", true).apply()
                    refreshAvatarOverlay()
                    status = "Avatar 2D personalizado salvo neste aparelho"
                }
                .onFailure { status = it.message ?: "Não foi possível criar o avatar" }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            val language = if (listenForTranslation) sourceLanguage.locale else "pt-BR"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o LINK")
            }
            if (SpeechRecognizer.isRecognitionAvailable(context)) speechLauncher.launch(intent)
            else { listening = false; listenForTranslation = false; status = "Reconhecimento de voz indisponível neste aparelho" }
        } else { listenForTranslation = false; status = "Permissão de microfone negada" }
    }

    fun startSpeech(translateSpeech: Boolean) {
        listenForTranslation = translateSpeech
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty() || isSending) return
        draft = ""
        messages.add(ChatLine(false, text))
        val localAction = AndroidActionRouter.run(context, text)
        if (localAction != null) {
            messages.add(ChatLine(true, localAction)); status = localAction; onSpeak(localAction, "pt")
            return
        }
        if (aiProvider == "chatgpt_plan" && chatGptEmail.isBlank()) {
            messages.add(ChatLine(true, "Conecte sua conta ChatGPT em Configurações para conversar usando o plano autorizado. A assinatura não compartilha o histórico do ChatGPT."))
            status = "ChatGPT ainda não conectado"
            return
        }
        if (aiProvider == "backend_api" && (backendUrl.isBlank() || accessToken.isBlank())) {
            messages.add(ChatLine(true, "O backend do LINK não está configurado. Você pode conectar o plano ChatGPT nas Configurações ou preencher a opção de API."))
            status = "IA não conectada"
            return
        }
        isSending = true
        status = "LINK está pensando..."
        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(20).map { ApiMessage(if (it.fromLink) "assistant" else "user", it.text) }
                val wantsWeb = asksForWebSearch(text)
                val reply = if (aiProvider == "chatgpt_plan") {
                    val model = selectedChatGptModel.ifBlank { chatGptModels.firstOrNull()?.slug ?: ChatGptPlanClient.availableModels(context).firstOrNull()?.slug.orEmpty() }
                    require(model.isNotBlank()) { "Nenhum modelo disponível nesta conta ChatGPT." }
                    ChatGptPlanClient.chat(context, model, text, history, wantsWeb)
                } else LinkApi.chat(backendUrl, accessToken, text, history, wantsWeb)
                messages.add(ChatLine(true, reply)); status = "IA conectada • $personality"; onSpeak(reply, "pt")
            } catch (error: Exception) {
                messages.add(ChatLine(true, "Não consegui concluir a solicitação: ${error.message ?: "falha na conexão"}")); status = "Falha na conexão"
            } finally { isSending = false }
        }
    }
    fun translateCurrent() {
        val text = translationInput.trim()
        if (text.isEmpty()) { status = "Digite ou fale um texto primeiro"; return }
        scope.launch {
            translating = true
            try {
                val detected = if (autoDetectLanguage) LocalTranslation.detectLanguage(text) else null
                val detectedOption = languageOptions.firstOrNull { it.id == detected }
                val sourceId = detectedOption?.id ?: sourceLanguage.id
                if (detectedOption != null) sourceLanguage = detectedOption
                translationOutput = LocalTranslation.translate(text, sourceId, targetLanguage.id)
                status = if (detectedOption != null) "Idioma detectado: ${detectedOption.name} → ${targetLanguage.name}" else "Usado ${sourceLanguage.name} → ${targetLanguage.name}"
            }
            catch (e: Exception) { status = e.message ?: "Falha na tradução" }
            finally { translating = false }
        }
    }

    LaunchedEffect(autoSendVoice) {
        if (autoSendVoice) {
            autoSendVoice = false
            sendMessage()
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Night, surface = Panel)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
            val wide = maxWidth >= 840.dp
            Scaffold(containerColor = Night, topBar = {
                TopAppBar(
                    title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinkAvatar(avatarFilePath, avatarCharacter, usePhotoAvatar, 42); Column { Text("LINK", fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("ASSISTENTE • ${page.label.uppercase()}", fontSize = 10.sp, color = Cyan, letterSpacing = 1.sp) }
                    } },
                    actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Configurações", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Night, titleContentColor = Color.White)
                )
            }, bottomBar = {
                if (page == LinkPage.CHAT) Row(Modifier.fillMaxWidth().background(Panel).imePadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { imageLauncher.launch("image/*") }) { Icon(Icons.Default.CameraAlt, "Ler imagem", tint = Cyan) }
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Converse com LINK...") }, maxLines = 3, shape = RoundedCornerShape(22.dp))
                    IconButton(onClick = { startSpeech(false) }) { Icon(Icons.Default.Mic, "Falar", tint = if (listening) Cyan else Color.White) }
                    IconButton(onClick = { sendMessage() }, enabled = !isSending) { Icon(Icons.Default.Send, "Enviar", tint = Cyan) }
                }
            }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    NavigationRail(containerColor = Panel, modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(8.dp))
                        LinkPage.entries.forEach { item ->
                            NavigationRailItem(
                                selected = page == item,
                                onClick = { page = item },
                                icon = { Icon(pageIcon(item), item.label, modifier = Modifier.size(22.dp)) },
                                label = { Text(item.label, maxLines = 1, fontSize = 9.sp) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                    Column(Modifier.fillMaxSize().weight(1f).padding(horizontal = if (wide) 30.dp else 10.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = if (status.startsWith("IA conectada") || status.startsWith("Traduzido")) Cyan else Color.LightGray, fontSize = 12.sp)
                        if (isSending || translating) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        Crossfade(targetState = page, label = "LINK navigation") { visiblePage ->
                            when (visiblePage) {
                                LinkPage.CHAT -> ChatScreen(
                                    messages = messages,
                                    avatarPath = avatarFilePath,
                                    avatarCharacter = avatarCharacter,
                                    usePhotoAvatar = usePhotoAvatar,
                                    onSuggestion = { draft = it }
                                )
                                LinkPage.TRANSLATE, LinkPage.LIVE -> TranslationScreen(
                                    live = visiblePage == LinkPage.LIVE, input = translationInput, onInput = { translationInput = it }, output = translationOutput,
                                    source = sourceLanguage, target = targetLanguage, chooseSource = { selectSource = true }, chooseTarget = { selectTarget = true },
                                    autoDetect = autoDetectLanguage, onAutoDetect = { autoDetectLanguage = it },
                                    swap = { val old = sourceLanguage; sourceLanguage = targetLanguage; targetLanguage = old },
                                    onTranslate = { translateCurrent() }, onSpeak = { if (translationOutput.isNotBlank()) onSpeak(translationOutput, targetLanguage.id) },
                                    onMic = { startSpeech(true) }, listening = listening, onImage = { imageLauncher.launch("image/*") },
                                    onAttribution = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com"))) }
                                )
                                LinkPage.MAPS -> MapsScreen(context)
                                LinkPage.VOICE -> VoiceScreen(onOpenChat = { page = LinkPage.CHAT }, onSpeak = { startSpeech(false) })
                            }
                        }
                    }
                }
            }
        }
        if (selectSource) LanguagePicker("Idioma original", sourceLanguage, { selectSource = false }) { sourceLanguage = it; selectSource = false }
        if (selectTarget) LanguagePicker("Traduzir para", targetLanguage, { selectTarget = false }) { targetLanguage = it; selectTarget = false }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Conexão e preferências") }, text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Atualização do LINK", fontSize = 12.sp, color = Cyan)
                OutlinedButton(onClick = {
                    scope.launch {
                        updateStatus = "Verificando versão oficial..."
                        updateStatus = try { InAppUpdater.checkAndInstall(context) }
                        catch (error: Exception) { error.message ?: "Não foi possível verificar a atualização." }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Verificar e atualizar") }
                Text(updateStatus, fontSize = 11.sp, color = Color.LightGray)
                Text("A instalação é confirmada pelo Android. O pacote é baixado pelo LINK quando uma versão oficial está disponível.", fontSize = 10.sp, color = Color.Gray)
                HorizontalDivider()
                Text("Avatar 2D e acesso rápido", fontSize = 12.sp, color = Cyan)
                AvatarPreview(avatarFilePath, avatarCharacter, usePhotoAvatar)
                Text("Personagem", color = Color.White, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Raposa", "Gato", "Robô", "Panda").forEach { option ->
                        FilterChip(selected = !usePhotoAvatar && avatarCharacter == option, onClick = { avatarCharacter = option; usePhotoAvatar = false; prefs.edit().putString("avatar_character", option).putBoolean("avatar_use_photo", false).apply(); refreshAvatarOverlay() }, label = { Text(option, fontSize = 10.sp) })
                    }
                }
                if (avatarFilePath.isNotBlank()) FilterChip(selected = usePhotoAvatar, onClick = { usePhotoAvatar = true; prefs.edit().putBoolean("avatar_use_photo", true).apply(); refreshAvatarOverlay() }, label = { Text("Minha imagem") })
                Text("Tema de fundo", color = Color.White, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Aurora", "Noite", "Pôr do sol").forEach { option ->
                        FilterChip(selected = avatarTheme == option, onClick = { avatarTheme = option; prefs.edit().putString("avatar_theme", option).apply(); refreshAvatarOverlay() }, label = { Text(option, fontSize = 10.sp) })
                    }
                }
                OutlinedButton(onClick = { avatarLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Criar avatar 2D a partir de uma imagem")
                }
                Text("A imagem é estilizada e guardada neste aparelho. O LINK não envia a foto para um serviço externo.", fontSize = 11.sp, color = Color.LightGray)
                Button(onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        prefs.edit().putBoolean("avatar_overlay_enabled", true).apply()
                        ContextCompat.startForegroundService(context, Intent(context, AvatarOverlayService::class.java))
                        status = "Avatar flutuante ativado • toque para conversar por voz"
                        showSettings = false
                    } else {
                        status = "Permita a janela flutuante nas configurações do Android e volte aqui para ativar"
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Ativar avatar flutuante") }
                Text("A permissão mostra o avatar sobre outros apps. Ao tocar, abre uma janela de voz do LINK. A ativação também mantém uma notificação do Android; use o botão Parar nela ou desative aqui.", fontSize = 11.sp, color = Color.LightGray)
                OutlinedButton(onClick = {
                    prefs.edit().putBoolean("avatar_overlay_enabled", false).apply()
                    context.stopService(Intent(context, AvatarOverlayService::class.java))
                    status = "Avatar flutuante desativado"
                }, modifier = Modifier.fillMaxWidth()) { Text("Desativar avatar flutuante") }
                HorizontalDivider()
                Text("Como o LINK responde", fontSize = 12.sp, color = Cyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = aiProvider == "chatgpt_plan", onClick = { aiProvider = "chatgpt_plan"; prefs.edit().putString("ai_provider", aiProvider).apply() }, label = { Text("Plano ChatGPT") })
                    FilterChip(selected = aiProvider == "backend_api", onClick = { aiProvider = "backend_api"; prefs.edit().putString("ai_provider", aiProvider).apply() }, label = { Text("API do LINK") })
                }
                if (chatGptEmail.isBlank()) Button(onClick = {
                    val host = context as? ComponentActivity
                    if (host == null) status = "Abra as configurações no app LINK para conectar ChatGPT"
                    else scope.launch {
                        status = "Aguardando autorização da conta ChatGPT..."
                        try {
                            chatGptEmail = ChatGptPlanClient.signIn(host)
                            chatGptModels = ChatGptPlanClient.availableModels(context)
                            if (selectedChatGptModel.isBlank()) {
                                selectedChatGptModel = chatGptModels.firstOrNull()?.slug.orEmpty()
                                prefs.edit().putString("chatgpt_model", selectedChatGptModel).apply()
                            }
                            aiProvider = "chatgpt_plan"; prefs.edit().putString("ai_provider", aiProvider).apply()
                            status = "Plano ChatGPT conectado • ${chatGptModels.size} modelos disponíveis"
                        } catch (error: Exception) { status = error.message ?: "Não foi possível conectar ChatGPT" }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Continuar com ChatGPT") }
                else {
                    Text("Plano conectado: $chatGptEmail", color = Cyan, fontSize = 12.sp)
                    OutlinedButton(onClick = { showModelPicker = true; scope.launch { runCatching { ChatGptPlanClient.availableModels(context) }.onSuccess { chatGptModels = it }.onFailure { status = it.message ?: "Falha ao carregar modelos" } } }, modifier = Modifier.fillMaxWidth()) {
                        Text("Modelo: ${chatGptModels.firstOrNull { it.slug == selectedChatGptModel }?.displayName ?: selectedChatGptModel.ifBlank { "Selecionar modelo" }}")
                    }
                    TextButton(onClick = { scope.launch { ChatGptPlanClient.signOut(context); chatGptEmail = ""; chatGptModels = emptyList(); selectedChatGptModel = ""; status = "Conta ChatGPT desconectada" } }) { Text("Desconectar conta ChatGPT") }
                }
                Text("Solicitações elegíveis usam a franquia autorizada no plano e os limites definidos em ChatGPT > Configurações > Uso. Isso não conecta o GPT personalizado nem suas conversas.", fontSize = 11.sp, color = Color.LightGray)
                if (showModelPicker) {
                    AlertDialog(
                        onDismissRequest = { showModelPicker = false },
                        title = { Text("Modelo ChatGPT") },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                chatGptModels.forEach { item ->
                                    TextButton(
                                        onClick = {
                                            selectedChatGptModel = item.slug
                                            prefs.edit().putString("chatgpt_model", item.slug).apply()
                                            showModelPicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (item.slug == selectedChatGptModel) "✓ ${item.displayName}" else item.displayName)
                                    }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("Fechar") } }
                    )
                }
                HorizontalDivider()
                Text("Backend API opcional", fontSize = 12.sp, color = Cyan)
                Text("Use somente se quiser usar a conexão API separada, que pode gerar cobranças fora do plano ChatGPT.", fontSize = 11.sp, color = Color.LightGray)
                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, placeholder = { Text("https://seu-backend.exemplo.com") }, singleLine = true, label = { Text("Backend HTTPS") })
                OutlinedTextField(value = accessToken, onValueChange = { accessToken = it }, placeholder = { Text("Token de acesso LINK") }, singleLine = true, label = { Text("Token") })
                Text("Tradução offline, leitura de imagens e reconhecimento de fala não usam créditos de IA. O reconhecimento depende dos idiomas instalados no Android.", fontSize = 11.sp, color = Color.LightGray)
                Text("This service may contain translations powered by Google. Google disclaims all warranties related to the translations, express or implied, including any warranties of accuracy, reliability, and any implied warranties of merchantability, fitness for a particular purpose and noninfringement.", fontSize = 10.sp, color = Color.Gray)
                HorizontalDivider(); Text("Personalidade da conversa")
                listOf("Adaptável", "Natural", "Técnica", "Objetiva").forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(personality == value, onClick = { personality = value }); Text(value) } }
                Text("A voz usa a melhor voz local pt-BR instalada no aparelho e velocidade mais natural. Vozes disponíveis variam por celular.", fontSize = 11.sp, color = Color.LightGray)
                HorizontalDivider(); Text("Autonomia: $autonomy")
                Text("Ações em outros aplicativos continuam sob seu controle.", fontSize = 11.sp, color = Color.Gray)
            }
        }, confirmButton = { TextButton(onClick = {
            prefs.edit().putString("backend_url", backendUrl.trim()).putString("avatar_character", avatarCharacter).putString("avatar_theme", avatarTheme).putBoolean("avatar_use_photo", usePhotoAvatar).apply()
            refreshAvatarOverlay()
            TokenCipher.write(context, accessToken.trim())
            showSettings = false
            status = if (backendUrl.isNotBlank() && accessToken.isNotBlank()) "Configuração salva • envie uma mensagem para testar" else "Modo local • IA em nuvem não configurada"
        }) { Text("Salvar") } })
    }
}

@Composable private fun LinkMark(size: Int) {
    Box(Modifier.size(size.dp).background(Brush.radialGradient(listOf(Color(0xFF1B6277), Color(0xFF10243A), Night)), CircleShape), contentAlignment = Alignment.Center) {
        Text("L", fontSize = (size * .52).sp, fontWeight = FontWeight.Light, color = Cyan)
    }
}

@Composable private fun LinkAvatar(path: String, character: String, usePhoto: Boolean, size: Int) {
    val bitmap = remember(path, usePhoto) { if (usePhoto && path.isNotBlank()) runCatching { BitmapFactory.decodeFile(path) }.getOrNull() else null }
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Avatar do LINK", Modifier.size(size.dp).clip(CircleShape).background(Panel), contentScale = ContentScale.Crop)
    else AvatarArtwork(character, Modifier.size(size.dp))
}

@Composable private fun AvatarPreview(path: String, character: String, usePhoto: Boolean) {
    val bitmap = remember(path) { if (path.isNotBlank()) runCatching { BitmapFactory.decodeFile(path) }.getOrNull() else null }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (usePhoto && bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Prévia do avatar 2D", modifier = Modifier.size(88.dp).clip(CircleShape).background(Panel, CircleShape), contentScale = ContentScale.Crop)
        else AvatarArtwork(character, Modifier.size(88.dp))
    }
}

private fun pageIcon(page: LinkPage) = when (page) {
    LinkPage.CHAT, LinkPage.VOICE -> Icons.Default.ChatBubbleOutline
    LinkPage.TRANSLATE -> Icons.Default.GTranslate
    LinkPage.LIVE -> Icons.Default.Mic
    LinkPage.MAPS -> Icons.Default.Explore
}

@Composable private fun ChatScreen(
    messages: List<ChatLine>,
    avatarPath: String,
    avatarCharacter: String,
    usePhotoAvatar: Boolean,
    onSuggestion: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(14.dp)); Text(if (messages.isEmpty()) "LINK • ASSISTENTE PESSOAL" else "CONVERSA", color = Cyan, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        if (messages.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val avatarBitmap = remember(avatarPath, usePhotoAvatar) {
                    if (usePhotoAvatar && avatarPath.isNotBlank()) runCatching { BitmapFactory.decodeFile(avatarPath) }.getOrNull() else null
                }
                if (avatarBitmap != null) {
                    Image(avatarBitmap.asImageBitmap(), "Avatar 2D personalizado", Modifier.size(112.dp).clip(CircleShape).background(Panel), contentScale = ContentScale.Crop)
                } else AvatarArtwork(avatarCharacter, Modifier.size(112.dp))
                Spacer(Modifier.height(16.dp))
                Text("Olá! Eu sou o LINK.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Posso ajudar você a pesquisar, traduzir, entender imagens e planejar o dia.", color = Color.LightGray, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Default.Explore, null, tint = Cyan, modifier = Modifier.size(17.dp))
                        Text("ChatGPT • pesquisa web para informações atuais", color = Color.LightGray, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    AssistChip(onClick = { onSuggestion("Me ajude a planejar meu dia.") }, label = { Text("Planejar meu dia", fontSize = 10.sp) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    AssistChip(onClick = { onSuggestion("Pesquise as principais notícias de hoje.") }, label = { Text("Pesquisar notícias", fontSize = 10.sp) })
                }
            }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages) { item -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.fromLink) Arrangement.Start else Arrangement.End) {
                val annotated = remember(item.text) {
                    val urlRegex = Regex("https?://[^\\s)]+")
                    val builder = buildAnnotatedString {
                        var last = 0
                        urlRegex.findAll(item.text).forEach { match ->
                            append(item.text.substring(last, match.range.first))
                            pushStringAnnotation("URL", match.value)
                            withStyle(SpanStyle(color = Cyan, textDecoration = TextDecoration.Underline)) { append(match.value) }
                            pop(); last = match.range.last + 1
                        }
                        append(item.text.substring(last))
                    }
                    builder
                }
                Surface(color = if (item.fromLink) Panel else Color(0xFF164457), shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 660.dp)) {
                    ClickableText(text = annotated, modifier = Modifier.padding(14.dp), style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp), onClick = { offset ->
                        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { url -> runCatching { uriHandler.openUri(url.trimEnd('.', ',', ')', ';')) } }
                    })
                }
            } }
        }
    }
}

@Composable private fun TranslationScreen(live: Boolean, input: String, onInput: (String) -> Unit, output: String,
    source: LanguageOption, target: LanguageOption, chooseSource: () -> Unit, chooseTarget: () -> Unit, swap: () -> Unit,
    autoDetect: Boolean, onAutoDetect: (Boolean) -> Unit,
    onTranslate: () -> Unit, onSpeak: () -> Unit, onMic: () -> Unit, listening: Boolean, onImage: () -> Unit, onAttribution: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(if (live) "Tradução por voz, diretamente no aparelho" else "Traduza texto e imagens sem créditos da API", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Detectar idioma automaticamente", color = Color.White, fontSize = 14.sp)
                Text("Texto/imagem: português, inglês, espanhol, francês, italiano e alemão", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(checked = autoDetect, onCheckedChange = onAutoDetect)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = chooseSource, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(source.name, maxLines = 1, softWrap = false) }
            IconButton(onClick = swap) { Icon(Icons.Default.SwapHoriz, "Trocar idiomas", tint = Cyan) }
            OutlinedButton(onClick = chooseTarget, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text(target.name, maxLines = 1, softWrap = false) }
        }
        OutlinedTextField(value = input, onValueChange = onInput, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8, label = { Text(if (live) "Fale ou escreva" else "Texto original") }, shape = RoundedCornerShape(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMic, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(4.dp)); Text(if (listening) "Ouvindo" else "Falar", maxLines = 1, softWrap = false) }
            OutlinedButton(onClick = onImage, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(4.dp)); Text("Ler imagem", maxLines = 1, softWrap = false) }
        }
        Button(onClick = onTranslate, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.GTranslate, null); Spacer(Modifier.width(8.dp)); Text("Traduzir") }
        if (output.isNotBlank()) Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TRADUÇÃO", color = Cyan, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onSpeak) { Icon(Icons.Default.VolumeUp, "Ouvir tradução", tint = Cyan) }
                }
                Text(output, color = Color.White, fontSize = 16.sp)
                TextButton(onClick = onAttribution, contentPadding = PaddingValues(0.dp)) { Text("Powered by Google Translate", fontSize = 11.sp) }
            }
        }
        Text("A tradução e a leitura de texto são feitas no aparelho. Na primeira utilização, o pacote de idioma pode ser baixado.", color = Color.Gray, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable private fun LanguagePicker(title: String, selected: LanguageOption, onDismiss: () -> Unit, onSelect: (LanguageOption) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column { languageOptions.forEach { option -> TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) { Text(if (option == selected) "✓  ${option.name}" else option.name, color = if (option == selected) Cyan else Color.White) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

@Composable private fun MapsScreen(context: Context) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp)); Text("LINK Maps", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Encontre um lugar e abra a navegação no app de mapas instalado. Sem chave paga do LINK.", color = Color.LightGray)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Endereço ou local") }, singleLine = true)
        Button(onClick = { openMaps(context, query) }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Explore, null); Spacer(Modifier.width(8.dp)); Text("Pesquisar no Maps") }
        OutlinedButton(onClick = { openMaps(context, "") }, modifier = Modifier.fillMaxWidth()) { Text("Ver mapa e minha localização") }
        Text("A navegação e o mapa são fornecidos pelo aplicativo externo, conforme sua conexão e permissões.", color = Color.Gray, fontSize = 12.sp)
    }
}

private fun openMaps(context: Context, query: String) {
    val uri = if (query.isBlank()) Uri.parse("geo:0,0?q=") else Uri.parse("geo:0,0?q=${Uri.encode(query)}")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    try { context.startActivity(intent) } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")))
    }
}

@Composable private fun VoiceScreen(onOpenChat: () -> Unit, onSpeak: () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LinkMark(124); Spacer(Modifier.height(20.dp)); Text("LINK Voice", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Converse por voz com o LINK", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSpeak) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("Começar a falar") }
        Text("O reconhecimento de fala pode usar o serviço de voz configurado no Android. Para conversa com respostas inteligentes, configure a conexão em ⚙.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp))
        Spacer(Modifier.height(12.dp)); TextButton(onClick = onOpenChat) { Text("Abrir conversa por texto") }
    }
}
