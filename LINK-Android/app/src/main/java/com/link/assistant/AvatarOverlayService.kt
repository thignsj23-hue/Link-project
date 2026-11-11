package com.link.assistant

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo

/** Lightweight user-started assistant bubble; it opens LINK for voice and manual controls. */
class AvatarOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: LinearLayout? = null
    private var overlayAllowed = false

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        overlayAllowed = true
        startStatusNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!overlayAllowed) { stopSelf(); return START_NOT_STICKY }
        else if (overlayAllowed) {
            bubble?.let { runCatching { windowManager.removeView(it) } }
            bubble = null
            showBubble()
        }
        return START_STICKY
    }

    private fun startStatusNotification() {
        val channelId = "link_avatar_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "Avatar flutuante do LINK", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(this, 42, Intent(this, AvatarOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.link.assistant.R.drawable.ic_link_notification)
            .setContentTitle("Avatar do LINK ativo")
            .setContentText("Toque no avatar para conversar. Use Parar para fechar.")
            .setOngoing(true)
            .addAction(0, "Parar", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun showBubble() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(0xFF101C30.toInt(), 0xFF55D6E8.toInt())
        }
        val avatar = ImageView(this).apply {
            val file = getFileStreamPath("link-avatar-2d.png")
            if (getSharedPreferences("link_settings", MODE_PRIVATE).getBoolean("avatar_use_photo", false) && file.exists()) setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            else setImageResource(com.link.assistant.R.drawable.ic_link_avatar_default)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Avatar do LINK"
        }
        root.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
        val shortcut = TextView(this).apply {
            text = "LINK"
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(shortcut, LinearLayout.LayoutParams(dp(48), dp(20)))
        root.setOnClickListener { openLinkForVoice() }
        root.setOnLongClickListener { stopSelf(); true }
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(dp(66), dp(86), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(12) }
        bubble = root
        windowManager.addView(root, params)
    }

    private fun openLinkForVoice() {
        val intent = Intent(this, VoiceOverlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24).toFloat()
        setColor(fill)
        setStroke(dp(2), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.link.assistant.action.STOP_AVATAR"
        private const val NOTIFICATION_ID = 7402
    }
}
