package com.link.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

/**
 * Listener experimental iniciado por ação explícita do usuário.
 * O Android mantém uma notificação enquanto o microfone está ativo.
 */
class WakeWordService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val notification = buildNotification("Escutando a palavra LINK. Toque para abrir.")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Reconhecimento de fala indisponível neste aparelho.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(this)
            }
        }
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (listening) return
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching {
            listening = true
            recognizer?.startListening(request)
        }.onFailure {
            listening = false
            handler.postDelayed({ startListening() }, 2_000)
        }
    }

    private fun stopListening() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.stopListening() }
        recognizer?.destroy()
        recognizer = null
    }

    private fun detect(values: List<String>?) {
        val phrase = values.orEmpty().joinToString(" ").lowercase()
        if (phrase.contains("link")) {
            updateNotification("Palavra LINK detectada. Toque para abrir o assistente.")
        }
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { listening = false; handler.postDelayed({ startListening() }, 500) }
    override fun onError(error: Int) { listening = false; handler.postDelayed({ startListening() }, 1_500) }
    override fun onResults(results: android.os.Bundle?) {
        detect(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
        listening = false
        handler.postDelayed({ startListening() }, 700)
    }
    override fun onPartialResults(partialResults: android.os.Bundle?) {
        detect(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
    }
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LINK - palavra de ativação", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("LINK")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.link.assistant.voice.STOP"
        private const val CHANNEL_ID = "link_wake_word"
        private const val NOTIFICATION_ID = 7202
    }
}
