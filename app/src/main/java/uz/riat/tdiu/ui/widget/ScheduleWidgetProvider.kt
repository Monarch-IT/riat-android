package uz.riat.tdiu.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.riat.tdiu.R
import uz.riat.tdiu.data.notification.ScheduleAlarmScheduler
import uz.riat.tdiu.data.repository.ScheduleRepository
import uz.riat.tdiu.ui.MainActivity
import java.util.Calendar

class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_WIDGET_MODE) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = context.getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
                val currentMode = prefs.getString("widget_mode_$appWidgetId", "next") ?: "next"
                val newMode = if (currentMode == "next") "current" else "next"
                prefs.edit().putString("widget_mode_$appWidgetId", newMode).apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * Called when the widget is resized by the user.
     * We use this to switch between compact and expanded (day schedule) layout.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private const val TAG = "ScheduleWidget"
        const val ACTION_TOGGLE_WIDGET_MODE = "uz.riat.tdiu.TOGGLE_WIDGET_MODE"

        // Height threshold in cells: at >=3 cells tall, switch to day view
        private const val EXPANDED_MIN_CELLS = 3

        /** Estimate cell count from dp height */
        private fun heightToCells(heightDp: Int): Int {
            // Android standard: each cell is ~74dp
            return ((heightDp + 30) / 74).coerceAtLeast(1)
        }

        /** Determine if this widget should show expanded day schedule */
        fun isExpandedMode(appWidgetManager: AppWidgetManager, appWidgetId: Int): Boolean {
            return try {
                val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val cells = heightToCells(minHeightDp)
                cells >= EXPANDED_MIN_CELLS
            } catch (e: Exception) {
                false
            }
        }

        /** Get today's day index (0=Mon, 5=Sat, -1=Sun) */
        private fun todayIndex(): Int {
            return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY    -> 0
                Calendar.TUESDAY   -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY  -> 3
                Calendar.FRIDAY    -> 4
                Calendar.SATURDAY  -> 5
                else               -> -1 // Sunday
            }
        }

        /** Day name for header */
        private val DAY_NAMES_FULL = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

        /** Compact layout resource by theme */
        private fun compactLayoutForTheme(theme: String): Int = when (theme) {
            "dark" -> R.layout.widget_schedule_dark
            "oled" -> R.layout.widget_schedule_oled
            else   -> R.layout.widget_schedule_light
        }

        /** Expanded layout resource by theme */
        private fun expandedLayoutForTheme(theme: String): Int = when (theme) {
            "dark" -> R.layout.widget_schedule_expanded
            "oled" -> R.layout.widget_schedule_expanded_oled
            else   -> R.layout.widget_schedule_expanded_light
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)

            val targetType = prefs.getString("widget_target_type_$appWidgetId", "group") ?: "group"
            val targetName = prefs.getString("widget_target_name_$appWidgetId", "")
                ?.ifEmpty { null }
                ?: prefs.getString("widget_group_$appWidgetId", "")
                ?.ifEmpty { null }
                ?: context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE).getString("user_group", null)
                ?: ScheduleRepository(context).getAvailableGroups().firstOrNull() ?: ""

            val mode    = prefs.getString("widget_mode_$appWidgetId", "next") ?: "next"
            val theme   = prefs.getString("widget_theme_$appWidgetId", "light") ?: "light"
            val opacity = prefs.getInt("widget_opacity_$appWidgetId", 100)

            // Save active target globally for alarm scheduler
            prefs.edit().apply {
                putString("active_target_type", targetType)
                putString("active_target_name", targetName)
                apply()
            }

            val expanded = isExpandedMode(appWidgetManager, appWidgetId)
            Log.d(TAG, "Update widget $appWidgetId — type=$targetType name=$targetName theme=$theme mode=$mode expanded=$expanded")

            if (expanded) {
                updateExpandedWidget(context, appWidgetManager, appWidgetId, targetType, targetName, theme, opacity)
            } else {
                updateCompactWidget(context, appWidgetManager, appWidgetId, targetType, targetName, mode, theme, opacity)
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // COMPACT MODE: shows next/current single lesson
        // ─────────────────────────────────────────────────────────────────────
        private fun updateCompactWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            targetType: String,
            targetName: String,
            mode: String,
            theme: String,
            opacity: Int
        ) {
            val layoutRes = compactLayoutForTheme(theme)
            val views = RemoteViews(context.packageName, layoutRes)

            val bgDrawable = getOpacityDrawable(theme, opacity)
            views.setInt(R.id.widgetRoot, "setBackgroundResource", bgDrawable)

            val badgeText = when (targetType) {
                "teacher" -> if (targetName.length > 18) targetName.take(16) + "…" else targetName
                "room"    -> "Ауд. $targetName"
                else      -> targetName
            }

            views.setTextViewText(R.id.tvWidgetStatusTag, if (mode == "current") "ТЕКУЩАЯ ПАРА" else "СЛЕДУЮЩАЯ ПАРА")
            views.setTextViewText(R.id.tvWidgetSubject,   "Синхронизация…")
            views.setTextViewText(R.id.tvWidgetDetails,   "ТГЭУ · Цифровая Экономика")
            views.setTextViewText(R.id.tvWidgetTime,      "")
            views.setTextViewText(R.id.tvWidgetGroup,     badgeText)
            views.setTextViewText(R.id.tvWidgetFooter,    "")

            val pi = PendingIntent.getActivity(
                context, appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pi)

            val toggleIntent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_WIDGET_MODE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val togglePi = PendingIntent.getBroadcast(
                context, appWidgetId + 30000, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetMode, togglePi)

            val notifyIntent = Intent(context, NotificationConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val notifyPi = PendingIntent.getActivity(
                context, appWidgetId + 20000, notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetNotify, notifyPi)

            safeUpdate(appWidgetManager, appWidgetId, views)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = ScheduleRepository(context)
                    val result = repo.resolveNextTargetLesson(targetType, targetName, mode)

                    withContext(Dispatchers.Main) {
                        val lesson = result.lesson
                        if (lesson != null) {
                            views.setTextViewText(R.id.tvWidgetStatusTag, result.statusTag)
                            views.setTextViewText(R.id.tvWidgetSubject,   lesson.subject)

                            val detailParts = mutableListOf<String>()
                            if (targetType != "room" && lesson.room.isNotEmpty())
                                detailParts.add(if (lesson.room.startsWith("Ауд", ignoreCase = true)) lesson.room else "Ауд. ${lesson.room}")
                            if (targetType != "teacher" && lesson.teacher.isNotEmpty())
                                detailParts.add(lesson.teacher)
                            if (targetType != "group" && lesson.group.isNotEmpty())
                                detailParts.add(lesson.group)

                            views.setTextViewText(R.id.tvWidgetDetails, detailParts.joinToString(" · "))
                            views.setTextViewText(R.id.tvWidgetTime,    result.timeLabel)
                            views.setTextViewText(R.id.tvWidgetGroup,   result.targetBadge)
                            views.setTextViewText(R.id.tvWidgetFooter,  lesson.type)
                        } else {
                            views.setTextViewText(R.id.tvWidgetStatusTag, "НЕТ ДАННЫХ")
                            views.setTextViewText(R.id.tvWidgetSubject,   "Расписание: $targetName")
                            views.setTextViewText(R.id.tvWidgetDetails,   "Занятия не найдены в базе данных")
                            views.setTextViewText(R.id.tvWidgetTime,      "")
                            views.setTextViewText(R.id.tvWidgetGroup,     result.targetBadge)
                            views.setTextViewText(R.id.tvWidgetFooter,    "")
                        }
                        safeUpdate(appWidgetManager, appWidgetId, views)
                    }

                    ScheduleAlarmScheduler.scheduleNextAlarm(context)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resolve next lesson", e)
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // EXPANDED MODE: shows full day schedule as ListView
        // ─────────────────────────────────────────────────────────────────────
        private fun updateExpandedWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            targetType: String,
            targetName: String,
            theme: String,
            opacity: Int
        ) {
            val layoutRes = expandedLayoutForTheme(theme)
            val views = RemoteViews(context.packageName, layoutRes)

            val bgDrawable = getOpacityDrawable(theme, opacity)
            views.setInt(R.id.widgetRoot, "setBackgroundResource", bgDrawable)

            val badgeText = when (targetType) {
                "teacher" -> if (targetName.length > 18) targetName.take(16) + "…" else targetName
                "room"    -> "Ауд. $targetName"
                else      -> targetName
            }

            // Day title
            val dayIdx = todayIndex()
            val dayLabel = if (dayIdx >= 0) ScheduleRepository.DAY_NAMES.getOrElse(dayIdx) { "Сегодня" }
                           else "Следующий день"
            views.setTextViewText(R.id.tvExpandedTitle, "РАСПИСАНИЕ · $dayLabel".uppercase())
            views.setTextViewText(R.id.tvExpandedGroup, badgeText)

            // Click on root → open app
            val openPi = PendingIntent.getActivity(
                context, appWidgetId + 40000,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openPi)
            views.setPendingIntentTemplate(R.id.lvWidgetLessons, openPi)

            // Notification settings button
            val notifyIntent = Intent(context, NotificationConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val notifyPi = PendingIntent.getActivity(
                context, appWidgetId + 20000, notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnExpandedNotify, notifyPi)

            // Connect ListView to RemoteViewsService
            val serviceIntent = Intent(context, ScheduleWidgetListService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Unique data URI per widget so Android caches separately
                data = android.net.Uri.parse("riat-widget://lessons/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.lvWidgetLessons, serviceIntent)
            views.setEmptyView(R.id.lvWidgetLessons, R.id.tvExpandedFooter)

            safeUpdate(appWidgetManager, appWidgetId, views)

            // Notify the collection adapter to refresh
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lvWidgetLessons)

            ScheduleAlarmScheduler.scheduleNextAlarm(context)
        }

        private fun getOpacityDrawable(theme: String, opacity: Int): Int {
            return when (theme) {
                "dark" -> when {
                    opacity >= 90 -> R.drawable.bg_widget_dark_100
                    opacity >= 70 -> R.drawable.bg_widget_dark_80
                    opacity >= 50 -> R.drawable.bg_widget_dark_60
                    opacity >= 30 -> R.drawable.bg_widget_dark_40
                    else          -> R.drawable.bg_widget_dark_20
                }
                "oled" -> when {
                    opacity >= 90 -> R.drawable.bg_widget_oled_100
                    opacity >= 70 -> R.drawable.bg_widget_oled_80
                    opacity >= 50 -> R.drawable.bg_widget_oled_60
                    opacity >= 30 -> R.drawable.bg_widget_oled_40
                    else          -> R.drawable.bg_widget_oled_20
                }
                else -> R.drawable.bg_widget_glass_light
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, ScheduleWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        private fun safeUpdate(mgr: AppWidgetManager, id: Int, views: RemoteViews) {
            try {
                mgr.updateAppWidget(id, views)
            } catch (e: Exception) {
                Log.e(TAG, "safeUpdate failed for widget $id", e)
            }
        }
    }
}
