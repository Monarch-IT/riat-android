package uz.riat.tdiu.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import uz.riat.tdiu.R
import uz.riat.tdiu.data.model.Lesson
import uz.riat.tdiu.data.repository.ScheduleRepository
import java.util.Calendar
import kotlinx.coroutines.runBlocking

class ScheduleWidgetListFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
        android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private val prefs get() = context.getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)

    private var lessons: List<Lesson> = emptyList()
    private var nowMinutes: Int = 0

    override fun onCreate() { loadData() }

    override fun onDataSetChanged() { loadData() }

    override fun onDestroy() {}

    private fun loadData() {
        val targetType = prefs.getString("widget_target_type_$appWidgetId", "group") ?: "group"
        val targetName = prefs.getString("widget_target_name_$appWidgetId", "")
            ?.ifEmpty { null }
            ?: prefs.getString("widget_group_$appWidgetId", "")
            ?.ifEmpty { null }
            ?: context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE).getString("user_group", null)
            ?: ""

        val cal = Calendar.getInstance()
        val nowDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val dayIdx = when (nowDayOfWeek) {
            Calendar.MONDAY    -> 0
            Calendar.TUESDAY   -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY  -> 3
            Calendar.FRIDAY    -> 4
            Calendar.SATURDAY  -> 5
            else               -> 0
        }
        nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        try {
            val repo = ScheduleRepository(context)
            lessons = runBlocking {
                repo.refreshFromNetwork()
                when (targetType) {
                    "teacher" -> repo.getScheduleForTeacher(targetName, dayIdx)
                    "room"    -> repo.getScheduleForRoom(targetName, dayIdx)
                    else      -> repo.getScheduleForGroup(targetName, dayIdx)
                }
            }
        } catch (e: Exception) {
            lessons = emptyList()
        }
    }

    override fun getCount(): Int = lessons.size

    override fun getViewAt(position: Int): RemoteViews {
        val theme = prefs.getString("widget_theme_$appWidgetId", "light") ?: "light"
        val itemLayoutRes = if (theme == "light") R.layout.widget_schedule_list_item_light else R.layout.widget_schedule_list_item

        val lesson = lessons.getOrNull(position)
            ?: return RemoteViews(context.packageName, itemLayoutRes)

        val rv = RemoteViews(context.packageName, itemLayoutRes)

        rv.setTextViewText(R.id.tvItemSlotNum, lesson.number)
        rv.setTextViewText(R.id.tvItemTimeStart, lesson.startTime)
        rv.setTextViewText(R.id.tvItemTimeEnd, lesson.endTime)
        rv.setTextViewText(R.id.tvItemSubject, lesson.subject)
        rv.setTextViewText(R.id.tvItemType, lesson.type)

        val targetType = prefs.getString("widget_target_type_$appWidgetId", "group") ?: "group"
        val detailParts = mutableListOf<String>()
        if (targetType != "room" && lesson.room.isNotEmpty()) {
            detailParts.add(if (lesson.room.startsWith("Ауд", ignoreCase = true)) lesson.room else "Ауд. ${lesson.room}")
        }
        if (targetType != "teacher" && lesson.teacher.isNotEmpty()) {
            detailParts.add(lesson.teacher)
        }
        if (targetType != "group" && lesson.group.isNotEmpty()) {
            detailParts.add(lesson.group)
        }
        rv.setTextViewText(R.id.tvItemDetails, detailParts.joinToString(" · "))

        val startMins = ScheduleRepository.timeToMinutes(lesson.startTime)
        val endMins   = ScheduleRepository.timeToMinutes(lesson.endTime)
        if (nowMinutes in startMins..endMins) {
            rv.setInt(R.id.tvItemSlotNum, "setTextColor", 0xFF00E1D9.toInt())
        } else if (startMins > nowMinutes) {
            rv.setInt(R.id.tvItemSlotNum, "setTextColor", 0xFF00E1D9.toInt())
        } else {
            rv.setInt(R.id.tvItemSlotNum, "setTextColor", 0xFF475569.toInt())
            rv.setInt(R.id.tvItemType, "setTextColor", 0xFF475569.toInt())
        }
        val fillIntent = Intent().apply {
            putExtra("lesson_slot", lesson.number)
        }
        rv.setOnClickFillInIntent(R.id.tvItemSubject, fillIntent)

        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
