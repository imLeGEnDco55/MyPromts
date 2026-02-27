package com.imlegendco.mypromts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class KeyboardSwitcherService : Service() {

    companion object {
        const val CHANNEL_ID = "keyboard_switcher_channel"
        const val NOTIFICATION_ID = 45

        fun start(context: Context) {
            val intent = Intent(context, KeyboardSwitcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeyboardSwitcherService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Keyboard Switcher"
            val descriptionText = "Notificación persistente para cambiar de teclado rápidamente"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Create an intent to open ActivateKeyboardActivity
        val targetIntent = Intent(this, ActivateKeyboardActivity::class.java).apply {
            // Usually we'd want new task clear task, but ActivateKeyboardActivity is translucent and finishes right away
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            targetIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use our icon
            .setContentTitle("Cambiar Teclado")
            .setContentText("Toca para abrir el selector de teclados")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Make it persistent
            .setAutoCancel(false)
            .setContentIntent(pendingIntent) // Tap action
            .build()
    }
}
