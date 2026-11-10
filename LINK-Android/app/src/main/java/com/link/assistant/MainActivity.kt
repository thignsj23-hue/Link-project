package com.link.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
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
import androidx.core.content.ContextCompat
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
        setContent { LinkApp(onSpeak = { speak(it) }) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("pt", "BR")
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "link_reply")
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

data class ChatLine(val fromLink: Boolean, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("link_settings", android.content.Context.MODE_PRIVATE) }
    val dao = remember { LinkDatabase.get(context).memoryDao() }

    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf(prefs.getString("personality", "Adaptável") ?: "Adaptável") }
    var backendUrl by remember { mutableStateOf(prefs.getString("backend_url", "") ?: "") }
    var accessToken by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Modo local gratuito") }
    var autonomyWeb by remember { mutableStateOf(prefs.getFloat("autonomy_web", 0f)) }
    var autonomyApps by remember { mutableStateOf(prefs.getFloat("autonomy_apps", 0f)) }
    var autonomyFiles by remember { mutableStateOf(prefs.getFloat("autonomy_files", 0f)) }
    var autonomyMessages by remember { mutableStateOf(prefs.getFloat("autonomy_messages", 0f)) }
    var autonomyPayments by remember { mutableStateOf(prefs.getFloat("autonomy_payments", 0f)) }
    val messages = remember { mutableStateListOf<ChatLine>() }

    LaunchedEffect(Unit) {
        dao.latest().reversed().forEach { messages.add(ChatLine(it.role == "assistant", it.content)) }
        if (messages.isEmpty()) messages.add(ChatLine(true, "Olá! Eu sou o LINK. No modo local posso conversar de forma básica, usar voz e guardar o histórico neste aparelho."))
    }

    fun addMessage(fromLink: Boolean, text: String) {
        messages.add(ChatLine(fromLink, text))
        scope.launch {
            dao.insert(MemoryEntity(role = if (fromLink) "assistant" else "user", content = text))
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = spoken
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o LINK")
            }
            if (SpeechRecognizer.isRecognitionAvailable(context)) speechLauncher.launch(intent)
            else { listening = false; status = "Reconhecimento de voz indisponível neste aparelho" }
        } else status = "Permissão de microfone negada"
    }

    val wakePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
            status = "Escuta experimental da palavra LINK ativa"
        } else status = "Permissão de microfone negada"
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (backendUrl.isBlank() || accessToken.isBlank()) {
                status = "Imagem selecionada, mas não enviada: configure backend HTTPS e token."
            } else {
                scope.launch {
                    status = "Enviando imagem para análise..."
                    try {
                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Não foi possível ler a imagem.")
                        val reply = LinkApi.analyzeImage(backendUrl, accessToken, bytes, mime, "Analise esta imagem e responda em português do Brasil.")
                        addMessage(false, "[Imagem enviada para análise]")
                        addMessage(true, reply)
                        onSpeak(reply)
                        status = "Análise visual concluída"
                    } catch (error: Exception) {
                        addMessage(true, "Não consegui analisar a imagem: ${error.message}")
                        status = "Falha na análise visual"
                    }
                }
            }
        }
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty() || isSending) return
        draft = ""
        addMessage(false, text)
        if (backendUrl.isBlank() || accessToken.isBlank()) {
            val local = when {
                text.lowercase().contains("olá") || text.lowercase().contains("oi") ->
                    "Olá! Estou no modo local gratuito. Posso guardar esta conversa e usar a voz nativa do Android."
                text.lowercase().contains("ajuda") ->
                    "No modo local tenho conversa básica, reconhecimento de fala, síntese de voz e memória no aparelho. IA avançada, visão e pesquisa exigem backend configurado."
                else ->
                    "Recebi sua mensagem. O modo local não usa API nem gera custo de OpenAI, mas também não executa um modelo generativo. Configure o backend para conectar a IA."
            }
            addMessage(true, local)
            onSpeak(local)
            status = "Modo local gratuito"
            return
        }
        isSending = true
        status = "LINK está pensando..."
        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(20).map {
                    ApiMessage(if (it.fromLink) "assistant" else "user", it.text)
                }
                val style = if (personality == "Adaptável") "adaptive" else personality.lowercase()
                val reply = LinkApi.chat(backendUrl, accessToken, text, history, style)
                addMessage(true, reply)
                status = "IA conectada • $personality"
                onSpeak(reply)
            } catch (error: Exception) {
                addMessage(true, "Não consegui concluir a solicitação: ${error.message}")
                status = "Falha na conexão"
            } finally {
                isSending = false
            }
        }
    }

    fun searchWeb() {
        val query = draft.trim()
        if (query.isEmpty()) {
            status = "Digite o que deseja pesquisar."
            return
        }
        draft = ""
        addMessage(false, "Pesquisar na internet: $query")
        if (backendUrl.isBlank() || accessToken.isBlank()) {
            addMessage(true, "A pesquisa na internet requer backend HTTPS, token e créditos/serviço habilitado na API.")
            return
        }
        scope.launch {
            status = "Pesquisando na internet..."
            try {
                val reply = LinkApi.webSearch(backendUrl, accessToken, query)
                addMessage(true, reply)
                onSpeak(reply)
                status = "Pesquisa concluída"
            } catch (error: Exception) {
                addMessage(true, "Falha na pesquisa: ${error.message}")
                status = "Falha na pesquisa"
            }
        }
    }

    fun startWakeWord() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        wakePermission.launch(permissions.toTypedArray())
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Night, surface = Panel)) {
        Scaffold(containerColor = Night, topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LINK", fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("ASSISTENTE INTELIGENTE • v0.2.0", fontSize = 10.sp, color = Cyan, letterSpacing = 1.sp)
                    }
                },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Configurações", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night, titleContentColor = Color.White)
            )
        }, bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { imageLauncher.launch("image/*") }) { Icon(Icons.Default.CameraAlt, "Selecionar imagem", tint = Cyan) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Converse com LINK...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(22.dp)
                )
                IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Icon(Icons.Default.Mic, "Falar", tint = if (listening) Cyan else Color.White)
                }
                IconButton(onClick = { sendMessage() }, enabled = !isSending) { Icon(Icons.Default.Send, "Enviar", tint = Cyan) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(104.dp).background(
                            Brush.radialGradient(listOf(Color(0xFF1B6277), Color(0xFF10243A), Night)),
                            CircleShape
                        ),
                        contentAlignment = Alignment.Center
                    ) { Text("L", fontSize = 50.sp, fontWeight = FontWeight.Light, color = Cyan) }
                }
                Spacer(Modifier.height(8.dp))
                Text("LINK está pronto para iniciar", Modifier.align(Alignment.CenterHorizontally), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(status, Modifier.align(Alignment.CenterHorizontally), color = if (status.startsWith("IA conectada")) Cyan else Color.Gray, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { searchWeb() }, modifier = Modifier.weight(1f)) { Text("Pesquisar") }
                    Button(onClick = { startWakeWord() }, modifier = Modifier.weight(1f)) { Text("Ativar LINK") }
                    OutlinedButton(onClick = {
                        context.startService(Intent(context, WakeWordService::class.java).setAction(WakeWordService.ACTION_STOP))
                        status = "Escuta da palavra LINK parada"
                    }, modifier = Modifier.weight(1f)) { Text("Parar") }
                }
                Spacer(Modifier.height(12.dp))
                Text("CONVERSA", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (isSending) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(messages) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.fromLink) Arrangement.Start else Arrangement.End) {
                            Surface(
                                color = if (item.fromLink) Panel else Color(0xFF164457),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.widthIn(max = 310.dp)
                            ) {
                                Text(item.text, Modifier.padding(13.dp), color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configurações do LINK") },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Backend seguro (HTTPS)", color = Cyan, fontSize = 12.sp)
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        placeholder = { Text("https://seu-backend.exemplo.com") },
                        singleLine = true
                    )
                    Text("Token de acesso do LINK", color = Cyan, fontSize = 12.sp)
                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        placeholder = { Text("Token definido no servidor") },
                        singleLine = true
                    )
                    Text("O token de acesso não é a chave OpenAI e não fica no APK.", fontSize = 10.sp, color = Color.Gray)
                    Divider()
                    Text("Personalidade")
                    listOf("Adaptável", "Natural", "Técnica", "Objetiva").forEach { value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(personality == value, onClick = { personality = value })
                            Text(value)
                        }
                    }
                    Divider()
                    Text("Autonomia por categoria")
                    AutonomySlider("Pesquisa na internet", autonomyWeb) {
                        autonomyWeb = it; prefs.edit().putFloat("autonomy_web", it).apply()
                    }
                    AutonomySlider("Aplicativos", autonomyApps) {
                        autonomyApps = it; prefs.edit().putFloat("autonomy_apps", it).apply()
                    }
                    AutonomySlider("Arquivos", autonomyFiles) {
                        autonomyFiles = it; prefs.edit().putFloat("autonomy_files", it).apply()
                    }
                    AutonomySlider("Mensagens", autonomyMessages) {
                        autonomyMessages = it; prefs.edit().putFloat("autonomy_messages", it).apply()
                    }
                    AutonomySlider("Pagamentos e compras", autonomyPayments) {
                        autonomyPayments = it; prefs.edit().putFloat("autonomy_payments", it).apply()
                    }
                    Text(
                        "0 = manual; 1 = assistido; 2 = automatizar tarefas não sensíveis. " +
                            "Envio de mensagens, pagamentos e ações irreversíveis sempre exigem confirmação explícita.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Button(onClick = {
                        scope.launch { dao.clear(); messages.clear(); status = "Memória local apagada" }
                    }) { Text("Apagar memória local") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("backend_url", backendUrl.trim()).putString("personality", personality).apply()
                    showSettings = false
                    status = if (backendUrl.isNotBlank() && accessToken.isNotBlank()) "Configuração pronta para testar" else "Modo local gratuito"
                }) { Text("Salvar") }
            }
        )
    }
}

@Composable
private fun AutonomySlider(label: String, value: Float, onValue: (Float) -> Unit) {
    Column {
        val labelValue = when (value.toInt()) { 0 -> "manual"; 1 -> "assistido"; else -> "controlado" }
        Text("$label: $labelValue", fontSize = 11.sp)
        Slider(value = value, onValueChange = onValue, valueRange = 0f..2f, steps = 1)
    }
}
