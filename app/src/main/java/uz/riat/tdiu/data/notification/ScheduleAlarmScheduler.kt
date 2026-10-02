package uz.riat.tdiu.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uz.riat.tdiu.data.repository.ScheduleRepository
import java.util.Calendar

object ScheduleAlarmScheduler {

    private const val TAG = "ScheduleAlarmScheduler"
    private const val ALARM_REQ_CODE = 9922

    private fun timeToMinutes(t: String): Int = try {
        val (h, m) = t.split(":").map { it.toInt() }
        h * 60 + m
    } catch (e: Exception) { 0 }

    fun scheduleNextAlarm(context: Context) {
        val prefs = context.getSharedPreferences("riat_notify_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("notify_enabled", true)
        if (!enabled) {
            cancelAlarms(context)
            return
        }

        val advanceMin = prefs.getInt("notify_advance_min", 15)

        val widgetPrefs = context.getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
        val targetType = widgetPrefs.getString("active_target_type", "group") ?: "group"
        val targetName = widgetPrefs.getString("active_target_name", "")
            ?.ifEmpty { null }
            ?: context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE).getString("user_group", null)
            ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ScheduleRepository(context)
                val result = repo.resolveNextTargetLesson(targetType, targetName)
                val lesson = result.lesson

                if (lesson != null && result.isToday) {
                    val cal = Calendar.getInstance()
                    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    val lessonStartMinutes = timeToMinutes(lesson.startTime)
                    val triggerMinutes = lessonStartMinutes - advanceMin

                    if (triggerMinutes > nowMinutes) {
                        val triggerCal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, triggerMinutes / 60)
                            set(Calendar.MINUTE, triggerMinutes % 60)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val intent = Intent(context, ScheduleNotificationReceiver::class.java).apply {
                            putExtra("subject", lesson.subject)
                            putExtra("room", lesson.room)
                            putExtra("teacher", lesson.teacher)
                            putExtra("startTime", lesson.startTime)
                            putExtra("type", lesson.type)
                            putExtra("advanceMin", advanceMin)
                            putExtra("group", targetName)
                        }

                        val pi = PendingIntent.getBroadcast(
                            context,
                            ALARM_REQ_CODE,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pi)
                        } else {
                            am.setExact(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pi)
                        }

                        Log.d(TAG, "Scheduled alarm for ${lesson.subject} at ${triggerCal.time}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarm", e)
            }
        }
    }

    fun cancelAlarms(context: Context) {
        try {
            val intent = Intent(context, ScheduleNotificationReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                ALARM_REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
            Log.d(TAG, "Cancelled upcoming alarms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm", e)
        }
    }
}
