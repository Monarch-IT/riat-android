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
        var targetName = widgetPrefs.getString("active_target_name", "")
            ?.ifEmpty { null }
            ?: context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE).getString("user_group", null)
            ?: ""

        if (targetName.isBlank()) {
            targetName = "AT-31/25r"
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ScheduleRepository(context)
                val cal = Calendar.getInstance()
                val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val nowDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val currentDayIdx = when (nowDayOfWeek) {
                    Calendar.MONDAY    -> 0
                    Calendar.TUESDAY   -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY  -> 3
                    Calendar.FRIDAY    -> 4
                    Calendar.SATURDAY  -> 5
                    else               -> 6
                }
                val todayLessons = when (targetType) {
                    "teacher" -> repo.getScheduleForTeacher(targetName, currentDayIdx)
                    "room"    -> repo.getScheduleForRoom(targetName, currentDayIdx)
                    else      -> repo.getScheduleForGroup(targetName, currentDayIdx)
                }
                var scheduledAny = false

                if (currentDayIdx in 0..5) {
                    for (lesson in todayLessons) {
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

                            val reqCode = ALARM_REQ_CODE + lesson.number.toIntOrNull().orZero()
                            val pi = PendingIntent.getBroadcast(
                                context,
                                reqCode,
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
                            scheduledAny = true
                            break
                        }
                    }

                    if (scheduledAny) return@launch
                }

                for (offset in 1..7) {
                    val nextDayIdx = (currentDayIdx + offset) % 7
                    if (nextDayIdx >= 6) continue
                    val nextLessons = when (targetType) {
                        "teacher" -> repo.getScheduleForTeacher(targetName, nextDayIdx)
                        "room"    -> repo.getScheduleForRoom(targetName, nextDayIdx)
                        else      -> repo.getScheduleForGroup(targetName, nextDayIdx)
                    }
                    if (nextLessons.isNotEmpty()) {
                        val firstLesson = nextLessons.first()
                        val lessonStartMinutes = timeToMinutes(firstLesson.startTime)
                        val triggerMinutes = lessonStartMinutes - advanceMin

                        val triggerCal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, offset)
                            set(Calendar.HOUR_OF_DAY, triggerMinutes / 60)
                            set(Calendar.MINUTE, triggerMinutes % 60)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val intent = Intent(context, ScheduleNotificationReceiver::class.java).apply {
                            putExtra("subject", firstLesson.subject)
                            putExtra("room", firstLesson.room)
                            putExtra("teacher", firstLesson.teacher)
                            putExtra("startTime", firstLesson.startTime)
                            putExtra("type", firstLesson.type)
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

                        Log.d(TAG, "Scheduled next day alarm for ${firstLesson.subject} at ${triggerCal.time}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarm", e)
            }
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    fun triggerTestNotification(context: Context) {
        val widgetPrefs = context.getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
        val targetName = widgetPrefs.getString("active_target_name", "")
            ?.ifEmpty { null }
            ?: context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE).getString("user_group", null)
            ?: "AT-31/25r"

        val intent = Intent(context, ScheduleNotificationReceiver::class.java).apply {
            putExtra("subject", "Цифровая экономика (Тест)")
            putExtra("room", "5/312")
            putExtra("teacher", "Амонов О. У.")
            putExtra("startTime", "08:00")
            putExtra("type", "Лекция")
            putExtra("advanceMin", 15)
            putExtra("group", targetName)
        }
        context.sendBroadcast(intent)
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
