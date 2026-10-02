package uz.riat.tdiu.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import uz.riat.tdiu.R
import uz.riat.tdiu.ui.MainActivity
import java.util.Locale

class ScheduleNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleReceiver"
        private const val CHANNEL_ID = "riat_schedule_channel"
        private const val CHANNEL_NAME = "Расписание занятий"
        private const val NOTIFY_ID = 4001
        private var tts: TextToSpeech? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra("subject") ?: "Занятие"
        val room = intent.getStringExtra("room").orEmpty()
        val teacher = intent.getStringExtra("teacher").orEmpty()
        val startTime = intent.getStringExtra("startTime").orEmpty()
        val type = intent.getStringExtra("type") ?: "Пара"
        val advanceMin = intent.getIntExtra("advanceMin", 15)

        val prefs = context.getSharedPreferences("riat_notify_prefs", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("notify_sound", true)
        val vibrateEnabled = prefs.getBoolean("notify_vibrate", true)
        val voiceEnabled = prefs.getBoolean("notify_voice", true)

        createNotificationChannel(context)

        val contentText = buildString {
            append("Через $advanceMin мин в $startTime: $subject")
            if (room.isNotEmpty()) append(" (Ауд. $room)")
            if (teacher.isNotEmpty()) append(" · $teacher")
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("РИАТ · $type через $advanceMin мин")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)

        if (!soundEnabled) {
            builder.setSilent(true)
        }
        if (!vibrateEnabled) {
            builder.setVibrate(longArrayOf(0))
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFY_ID, builder.build())

        // Voice TTS announcement
        if (voiceEnabled) {
            speakReminder(context, "Внимание! Через $advanceMin минут начинается $type по предмету $subject. Аудитория $room.")
        }

        // Reschedule next
        ScheduleAlarmScheduler.scheduleNextAlarm(context)
    }

    private fun speakReminder(context: Context, text: String) {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ru", "RU"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SCHEDULE_ALARM")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Оповещения перед началом занятий факультета"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
