package com.example.gasml.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.gasml.MainActivity

class NotificationHelper(private val context: Context) {
    private val criticalChannelId = "gas_leak_emergency_v5001"
    private val infoChannelId = "general_updates_v5001"

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        // Static alarm player so it persists across NotificationHelper instances
        // and can be stopped from any context
        private var alarmPlayer: MediaPlayer? = null
        private var vibrator: Vibrator? = null

        private fun stopAlarmSound() {
            try {
                alarmPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (e: Exception) {
                Log.e("NotificationHelper", "Error stopping alarm player", e)
            }
            alarmPlayer = null

            try {
                vibrator?.cancel()
            } catch (e: Exception) {
                Log.e("NotificationHelper", "Error stopping vibrator", e)
            }
            vibrator = null
        }
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // 1. Critical Leak Channel — NO sound on channel (we play it ourselves via MediaPlayer)
                val leakChannel = NotificationChannel(
                    criticalChannelId,
                    "\uD83D\uDEA8 CRITICAL Gas Leak Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent high-priority alarms for gas leak detection"
                    setSound(null, null) // Disable channel sound — MediaPlayer handles it
                    enableVibration(false) // Disable channel vibration — we handle it manually
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableLights(true)
                    lightColor = Color.RED
                }

                // 2. Info Channel
                val infoChannel = NotificationChannel(
                    infoChannelId,
                    "General Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for orders and messages"
                    enableVibration(true)
                }

                notificationManager.createNotificationChannel(leakChannel)
                notificationManager.createNotificationChannel(infoChannel)
                Log.d("NotificationHelper", "Channels v5001 created")
            } catch (e: Exception) {
                Log.e("NotificationHelper", "Error creating channels", e)
            }
        }
    }

    private fun startAlarmSound() {
        // Stop any existing alarm first
        stopAlarmSound()

        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (soundUri != null) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                alarmPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(context, soundUri)
                    isLooping = true  // Keep playing until explicitly stopped
                    prepare()
                    start()
                }
                Log.d("NotificationHelper", "Alarm sound started (looping)")
            }
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to start alarm sound", e)
        }

        // Start vibration pattern (looping)
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            vibrator = vib
            val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000, 500, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 = repeat from index 0
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
            Log.d("NotificationHelper", "Vibration started (looping)")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to start vibration", e)
        }
    }

    fun showLeakAlert() {
        try {
            Log.d("NotificationHelper", "Triggering Leak Alert with alarm sound")

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_LEAK_DIALOG", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                911,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Build notification WITHOUT sound (MediaPlayer handles it)
            val builder = NotificationCompat.Builder(context, criticalChannelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("\uD83D\uDEA8 CRITICAL GAS LEAK DETECTED!")
                .setContentText("DANGER: A gas leak has been detected. Check your kitchen immediately.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setColor(Color.RED)
                .setLights(Color.RED, 500, 500)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Acknowledge Safety", pendingIntent)

            val notification = builder.build()
            notification.flags = notification.flags or Notification.FLAG_INSISTENT

            notificationManager.notify(911, notification)

            // Start the looping alarm sound and vibration independently
            startAlarmSound()

        } catch (e: Exception) {
            Log.e("NotificationHelper", "showLeakAlert failed", e)
        }
    }

    fun showOrderNotification(title: String, message: String) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, infoChannelId)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Order notification error", e)
        }
    }

    fun showChatNotification(senderName: String, message: String) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                senderName.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, infoChannelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("New message from $senderName")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(senderName.hashCode(), builder.build())
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Chat notification error", e)
        }
    }

    fun clearAlert() {
        try {
            Log.d("NotificationHelper", "Clearing alert 911 and stopping alarm sound")
            notificationManager.cancel(911)
            stopAlarmSound()
        } catch (e: Exception) {
            Log.e("NotificationHelper", "clearAlert error", e)
        }
    }
}
