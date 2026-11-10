package com.link.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
        val locale = Locale(language)
        val available = engine.voices.orEmpty().filter { it.locale.language == locale.language }
        val voice: Voice? = available.filter { !it.isNetworkConnectionRequired }
            .maxByOrNull { it.quality }
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
    var page by remember { mutableStateOf(LinkPage.CHAT) }
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var listenForTranslation by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf("Adaptável") }
    var autonomy by remember { mutableStateOf("Assistido") }
    var backendUrl by remember { mutableStateOf(prefs.getString("backend_url", "") ?: "") }
    var accessToken by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Modo local disponível") }
    var autoSendVoice by remember { mutableStateOf(false) }
    var translationInput by remember { mutableStateOf("") }
    var translationOutput by remember { mutableStateOf("") }
    var sourceLanguage by remember { mutableStateOf(languageOptions[0]) }
    var targetLanguage by remember { mutableStateOf(languageOptions[1]) }
    var translating by remember { mutableStateOf(false) }
    var selectSource by remember { mutableStateOf(false) }
    var selectTarget by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf(ChatLine(true, "Olá! Sou o LINK. Posso conversar quando o backend estiver configurado. Tradução offline, voz, leitura de texto em imagens e mapas estão no menu.")) }

    suspend fun translate(text: String): String = LocalTranslation.translate(text, sourceLanguage.id, targetLanguage.id)

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            if (listenForTranslation) {
                translationInput = spoken
                scope.launch {
                    translating = true
                    try {
                        translationOutput = translate(spoken)
                        status = "Traduzido no aparelho"
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
                    translationOutput = translate(extracted)
                    status = "Texto detectado e traduzido no aparelho"
                } catch (e: Exception) { status = e.message ?: "Não foi possível ler essa imagem" }
                finally { translating = false }
            }
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
        if (backendUrl.isBlank() || accessToken.isBlank()) {
            messages.add(ChatLine(true, "Modo local: a conversa inteligente na nuvem precisa de conexão. Use Traduzir, Link ao vivo, Link Voice ou Maps sem créditos da API."))
            status = "Modo local • IA em nuvem desconectada"
            return
        }
        isSending = true
        status = "LINK está pensando..."
        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(20).map { ApiMessage(if (it.fromLink) "assistant" else "user", it.text) }
                val reply = LinkApi.chat(backendUrl, accessToken, text, history)
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
            try { translationOutput = translate(text); status = "Traduzido no aparelho • ${sourceLanguage.name} → ${targetLanguage.name}" }
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
                        LinkMark(42); Column { Text("LINK", fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("ASSISTENTE • ${page.label.uppercase()}", fontSize = 10.sp, color = Cyan, letterSpacing = 1.sp) }
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
                if (!wide) NavigationBar(containerColor = Panel) {
                    LinkPage.entries.forEach { item -> NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(pageIcon(item), item.label) }, label = { Text(item.label, maxLines = 1) }) }
                }
            }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (wide) NavigationRail(containerColor = Panel) {
                        LinkPage.entries.forEach { item -> NavigationRailItem(selected = page == item, onClick = { page = item }, icon = { Icon(pageIcon(item), item.label) }, label = { Text(item.label) }) }
                    }
                    Column(Modifier.fillMaxSize().weight(1f).padding(horizontal = if (wide) 30.dp else 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = if (status.startsWith("IA conectada") || status.startsWith("Traduzido")) Cyan else Color.LightGray, fontSize = 12.sp)
                        if (isSending || translating) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        when (page) {
                            LinkPage.CHAT -> ChatScreen(messages)
                            LinkPage.TRANSLATE, LinkPage.LIVE -> TranslationScreen(
                                live = page == LinkPage.LIVE, input = translationInput, onInput = { translationInput = it }, output = translationOutput,
                                source = sourceLanguage, target = targetLanguage, chooseSource = { selectSource = true }, chooseTarget = { selectTarget = true },
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
        if (selectSource) LanguagePicker("Idioma original", sourceLanguage, { selectSource = false }) { sourceLanguage = it; selectSource = false }
        if (selectTarget) LanguagePicker("Traduzir para", targetLanguage, { selectTarget = false }) { targetLanguage = it; selectTarget = false }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Conexão e preferências") }, text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("IA na nuvem (opcional)", fontSize = 12.sp, color = Cyan)
                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, placeholder = { Text("https://seu-backend.exemplo.com") }, singleLine = true, label = { Text("Backend HTTPS") })
                OutlinedTextField(value = accessToken, onValueChange = { accessToken = it }, placeholder = { Text("Token de acesso LINK") }, singleLine = true, label = { Text("Token") })
                Text("Tradução offline, leitura de imagens e fala não usam créditos da API. O reconhecimento de voz depende dos idiomas instalados no Android.", fontSize = 11.sp, color = Color.LightGray)
                Text("This service may contain translations powered by Google. Google disclaims all warranties related to the translations, express or implied, including any warranties of accuracy, reliability, and any implied warranties of merchantability, fitness for a particular purpose and noninfringement.", fontSize = 10.sp, color = Color.Gray)
                HorizontalDivider(); Text("Personalidade da conversa")
                listOf("Adaptável", "Natural", "Técnica", "Objetiva").forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(personality == value, onClick = { personality = value }); Text(value) } }
                Text("A voz usa a melhor voz local pt-BR instalada no aparelho e velocidade mais natural. Vozes disponíveis variam por celular.", fontSize = 11.sp, color = Color.LightGray)
                HorizontalDivider(); Text("Autonomia: $autonomy")
                Text("Ações em outros aplicativos continuam sob seu controle.", fontSize = 11.sp, color = Color.Gray)
            }
        }, confirmButton = { TextButton(onClick = {
            prefs.edit().putString("backend_url", backendUrl.trim()).apply(); showSettings = false
            status = if (backendUrl.isNotBlank() && accessToken.isNotBlank()) "Configuração salva • envie uma mensagem para testar" else "Modo local • IA em nuvem não configurada"
        }) { Text("Salvar") } })
    }
}

@Composable private fun LinkMark(size: Int) {
    Box(Modifier.size(size.dp).background(Brush.radialGradient(listOf(Color(0xFF1B6277), Color(0xFF10243A), Night)), CircleShape), contentAlignment = Alignment.Center) {
        Text("L", fontSize = (size * .52).sp, fontWeight = FontWeight.Light, color = Cyan)
    }
}

private fun pageIcon(page: LinkPage) = when (page) {
    LinkPage.CHAT, LinkPage.VOICE -> Icons.Default.ChatBubbleOutline
    LinkPage.TRANSLATE -> Icons.Default.GTranslate
    LinkPage.LIVE -> Icons.Default.Mic
    LinkPage.MAPS -> Icons.Default.Explore
}

@Composable private fun ChatScreen(messages: List<ChatLine>) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(14.dp)); Text("CONVERSA", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages) { item -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.fromLink) Arrangement.Start else Arrangement.End) {
                Surface(color = if (item.fromLink) Panel else Color(0xFF164457), shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 660.dp)) { Text(item.text, Modifier.padding(14.dp), color = Color.White, fontSize = 15.sp) }
            } }
        }
    }
}

@Composable private fun TranslationScreen(live: Boolean, input: String, onInput: (String) -> Unit, output: String,
    source: LanguageOption, target: LanguageOption, chooseSource: () -> Unit, chooseTarget: () -> Unit, swap: () -> Unit,
    onTranslate: () -> Unit, onSpeak: () -> Unit, onMic: () -> Unit, listening: Boolean, onImage: () -> Unit, onAttribution: () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(if (live) "Tradução por voz, diretamente no aparelho" else "Traduza texto e imagens sem créditos da API", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = chooseSource, modifier = Modifier.weight(1f)) { Text(source.name, maxLines = 1) }
            IconButton(onClick = swap) { Icon(Icons.Default.SwapHoriz, "Trocar idiomas", tint = Cyan) }
            OutlinedButton(onClick = chooseTarget, modifier = Modifier.weight(1f)) { Text(target.name, maxLines = 1) }
        }
        OutlinedTextField(value = input, onValueChange = onInput, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), minLines = 4, maxLines = 10, label = { Text(if (live) "Fale ou escreva" else "Texto original") }, shape = RoundedCornerShape(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onMic, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (listening) "Ouvindo…" else "Falar") }
            OutlinedButton(onClick = onImage, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Ler imagem") }
            Button(onClick = onTranslate, modifier = Modifier.weight(1f)) { Icon(Icons.Default.GTranslate, null); Spacer(Modifier.width(6.dp)); Text("Traduzir com Google") }
        }
        if (output.isNotBlank()) Surface(Modifier.fillMaxWidth().weight(1f, fill = false), color = Panel, shape = RoundedCornerShape(18.dp)) {
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

