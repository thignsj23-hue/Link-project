package com.link.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import android.view.WindowManager

private val VoiceCyan = Color(0xFF55D6E8)
private val VoiceThemes = mapOf(
    "Aurora" to listOf(Color(0xFF06283D), Color(0xFF14245C), Color(0xFF111827)),
    "Noite" to listOf(Color(0xFF070B16), Color(0xFF18243B), Color(0xFF111827)),
    "Pôr do sol" to listOf(Color(0xFF421B53), Color(0xFF913B64), Color(0xFF201832))
)

class VoiceOverlayActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    var afterSpeech: (() -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { VoiceOverlayScreen(onSpeak = ::speak) }
        val width = (resources.displayMetrics.widthPixels * .92f).toInt()
        val height = minOf((resources.displayMetrics.heightPixels * .78f).toInt(), (620 * resources.displayMetrics.density).toInt())
        window.setLayout(width, height)
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            chooseVoice()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { runOnUiThread { afterSpeech?.invoke(); afterSpeech = null } }
                @Deprecated("Deprecated by the Android speech API")
                override fun onError(utteranceId: String?) { runOnUiThread { afterSpeech?.invoke(); afterSpeech = null } }
            })
        }
    }
    private fun chooseVoice() {
        val engine = tts ?: return
        val voice = engine.voices.orEmpty().filter { it.locale.language == "pt" && !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
        if (voice != null) engine.voice = voice else engine.language = Locale("pt", "BR")
        engine.setSpeechRate(.94f); engine.setPitch(.96f)
    }
    private fun speak(text: String, nextTurn: (() -> Unit)?) {
        afterSpeech = nextTurn
        chooseVoice()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "link_avatar_reply")
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

@Composable private fun VoiceOverlayScreen(onSpeak: (String, (() -> Unit)?) -> Unit) {
    val context = LocalContext.current
    val activity = context as VoiceOverlayActivity
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("link_settings", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val avatarPath = remember { prefs.getString("avatar_2d_path", "") ?: "" }
    val usePhotoAvatar = remember { prefs.getBoolean("avatar_use_photo", false) }
    val character = remember { prefs.getString("avatar_character", "Raposa") ?: "Raposa" }
    val theme = remember { prefs.getString("avatar_theme", "Aurora") ?: "Aurora" }
    val messages = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf("Toque no microfone e fale com o LINK") }
    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var continueVoice by remember { mutableStateOf(prefs.getBoolean("avatar_continue_voice", true)) }
    var restartVoiceAfterSpeech by remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (heard.isNullOrBlank()) { status = "Não consegui entender. Toque no microfone para tentar de novo."; return@rememberLauncherForActivityResult }
        messages.add("Você: $heard")
        val localAction = AndroidActionRouter.run(context, heard)
        if (localAction != null) {
            status = localAction; messages.add("LINK: $localAction"); onSpeak(localAction, null)
            return@rememberLauncherForActivityResult
        }
        val provider = prefs.getString("ai_provider", "chatgpt_plan") ?: "chatgpt_plan"
        val url = prefs.getString("backend_url", "")?.trim().orEmpty()
        val token = TokenCipher.read(context).orEmpty()
        val planEmail = ChatGptPlanClient.signedInEmail(context)
        if (provider == "chatgpt_plan" && planEmail.isNullOrBlank()) { status = "Conecte sua conta ChatGPT nas configurações do LINK para responder."; onSpeak(status, null); return@rememberLauncherForActivityResult }
        if (provider == "backend_api" && (url.isBlank() || token.isBlank())) { status = "Configure a API do LINK nas configurações para receber respostas faladas."; onSpeak(status, null); return@rememberLauncherForActivityResult }
        thinking = true; status = "LINK está pensando..."
        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(12).map { line ->
                    if (line.startsWith("LINK: ")) ApiMessage("assistant", line.removePrefix("LINK: "))
                    else ApiMessage("user", line.removePrefix("Você: "))
                }
                val webSearch = asksForWebSearch(heard)
                val model = prefs.getString("chatgpt_model", "")?.ifBlank { null }
                val answer = if (provider == "chatgpt_plan") {
                    val selectedModel = model ?: ChatGptPlanClient.availableModels(context).firstOrNull()?.slug.orEmpty()
                    require(selectedModel.isNotBlank()) { "Nenhum modelo disponível na sua conta ChatGPT." }
                    ChatGptPlanClient.chat(context, selectedModel, heard, history, webSearch)
                } else LinkApi.chat(url, token, heard, history, webSearch)
                messages.add("LINK: $answer")
                status = "Resposta pronta • toque para falar novamente"
                onSpeak(answer) { if (continueVoice) restartVoiceAfterSpeech = true }
            } catch (error: Exception) {
                status = error.message ?: "Não consegui conectar ao LINK."
            } finally { thinking = false }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) { status = "Serviço de reconhecimento de voz indisponível"; return@rememberLauncherForActivityResult }
            listening = true; status = "Ouvindo..."
            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com o LINK")
            })
        } else status = "Permita o microfone para conversar com o avatar."
    }
    LaunchedEffect(Unit) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(restartVoiceAfterSpeech) {
        if (restartVoiceAfterSpeech) {
            restartVoiceAfterSpeech = false
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val bitmap = remember(avatarPath, usePhotoAvatar) { if (usePhotoAvatar && avatarPath.isNotBlank()) runCatching { android.graphics.BitmapFactory.decodeFile(avatarPath) }.getOrNull() else null }
    val backdrop = VoiceThemes[theme] ?: VoiceThemes.getValue("Aurora")

    MaterialTheme(colorScheme = darkColorScheme(primary = VoiceCyan, background = Color(0xFF080D18), surface = Color(0xFF111C2D))) {
        Surface(Modifier.fillMaxSize(), color = Color.Transparent, shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(backdrop), RoundedCornerShape(30.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LINK • $character", color = Color.White, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { activity.finish() }) { Icon(Icons.Default.Close, "Fechar avatar", tint = Color.White) }
                }
                Spacer(Modifier.height(10.dp))
                if (bitmap != null) Image(bitmap.asImageBitmap(), "Avatar 2D", Modifier.size(132.dp).background(Color(0xFF101C30), CircleShape), contentScale = ContentScale.Crop)
                else AvatarArtwork(character, Modifier.size(132.dp))
                Text("Seu assistente por voz", color = VoiceCyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Surface(Modifier.fillMaxWidth().weight(1f).padding(vertical = 14.dp), color = Color(0xCC101C30), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(status, color = Color.LightGray, fontSize = 12.sp)
                        if (thinking) LinearProgressIndicator(Modifier.fillMaxWidth())
                        messages.takeLast(4).forEach { message ->
                            val annotated = remember(message) {
                                val pattern = Regex("https?://[^\\s)]+")
                                buildAnnotatedString {
                                    var last = 0
                                    pattern.findAll(message).forEach { match ->
                                        append(message.substring(last, match.range.first))
                                        pushStringAnnotation("URL", match.value)
                                        withStyle(SpanStyle(color = VoiceCyan, textDecoration = TextDecoration.Underline)) { append(match.value) }
                                        pop(); last = match.range.last + 1
                                    }
                                    append(message.substring(last))
                                }
                            }
                            ClickableText(annotated, style = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp), maxLines = 4, onClick = { offset ->
                                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.item?.let { url -> runCatching { uriHandler.openUri(url.trimEnd('.', ',', ')', ';')) } }
                            })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ouvir próximo turno automaticamente", color = Color.White, fontSize = 12.sp)
                    Switch(continueVoice, onCheckedChange = { continueVoice = it; prefs.edit().putBoolean("avatar_continue_voice", it).apply() })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { activity.finish(); context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.OpenInFull, null); Spacer(Modifier.width(6.dp)); Text("Abrir LINK")
                    }
                    FilledIconButton(onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }, enabled = !listening && !thinking, modifier = Modifier.size(58.dp)) {
                        Icon(Icons.Default.Mic, if (listening) "Ouvindo" else "Falar", tint = Color(0xFF07111C), modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

