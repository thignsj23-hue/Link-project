package com.link.assistant

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    private fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "link_reply") }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

data class ChatLine(val fromLink: Boolean, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var personality by remember { mutableStateOf("Adaptável") }
    var autonomy by remember { mutableStateOf("Assistido") }
    var status by remember { mutableStateOf("Online quando conectado") }
    val messages = remember { mutableStateListOf(ChatLine(true, "Olá! Eu sou o LINK. Como posso ajudar?")) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = spoken
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            messages.add(ChatLine(false, "Imagem selecionada: $uri"))
            messages.add(ChatLine(true, "Recebi a imagem. A análise visual será ativada quando conectarmos o serviço de visão do LINK."))
        }
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

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return
        messages.add(ChatLine(false, text))
        draft = ""
        val reply = "Entendi. A interface inicial do LINK está funcionando. Para responder com inteligência, precisamos conectar o motor de IA e o backend seguro."
        messages.add(ChatLine(true, reply))
        onSpeak(reply)
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Night, surface = Panel)) {
        Scaffold(containerColor = Night, topBar = {
            TopAppBar(
                title = { Column { Text("LINK", fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text("ASSISTENTE INTELIGENTE", fontSize = 10.sp, color = Cyan, letterSpacing = 1.5.sp) } },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Configurações", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night, titleContentColor = Color.White)
            )
        }, bottomBar = {
            Row(Modifier.fillMaxWidth().background(Panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { imageLauncher.launch("image/*") }) { Icon(Icons.Default.CameraAlt, "Selecionar imagem", tint = Cyan) }
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Converse com LINK...") }, maxLines = 3, shape = RoundedCornerShape(22.dp))
                IconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }) { Icon(Icons.Default.Mic, "Falar", tint = if (listening) Cyan else Color.White) }
                IconButton(onClick = { sendMessage() }) { Icon(Icons.Default.Send, "Enviar", tint = Cyan) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(116.dp).background(Brush.radialGradient(listOf(Color(0xFF1B6277), Color(0xFF10243A), Night)), CircleShape), contentAlignment = Alignment.Center) {
                        Text("L", fontSize = 54.sp, fontWeight = FontWeight.Light, color = Cyan)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("LINK está pronto para iniciar", Modifier.align(Alignment.CenterHorizontally), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(status, Modifier.align(Alignment.CenterHorizontally), color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Text("CONVERSA", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(messages) { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.fromLink) Arrangement.Start else Arrangement.End) {
                            Surface(color = if (item.fromLink) Panel else Color(0xFF164457), shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 310.dp)) {
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
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Personalidade")
                listOf("Adaptável", "Natural", "Técnica", "Objetiva").forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(personality == value, onClick = { personality = value }); Text(value) } }
                Divider()
                Text("Autonomia")
                listOf("Conservador", "Assistido", "Autônomo controlado").forEach { value -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(autonomy == value, onClick = { autonomy = value }); Text(value) } }
                Text("As preferências serão persistidas em uma próxima etapa.", fontSize = 11.sp, color = Color.Gray)
            } },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Concluir") } }
        )
    }
}
