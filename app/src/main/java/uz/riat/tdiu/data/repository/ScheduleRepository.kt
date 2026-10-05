package uz.riat.tdiu.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import uz.riat.tdiu.data.model.Lesson
import java.io.InputStreamReader
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class NextLessonResult(
    val lesson: Lesson?,
    val statusTag: String,
    val timeLabel: String,
    val isToday: Boolean,
    val targetBadge: String
)

class ScheduleRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val BASE_URL = "https://tsue-digital-economy.web.app"

    companion object {
        private var cachedJson: String? = null
        private var cachedRoot: JSONObject? = null

        val SLOTS = listOf(
            Pair("08:00", "09:20"),
            Pair("09:30", "10:50"),
            Pair("11:00", "12:20"),
            Pair("13:00", "14:20"),
            Pair("14:30", "15:50"),
            Pair("16:00", "17:20"),
            Pair("17:30", "18:50"),
            Pair("19:00", "20:20")
        )

        val DAY_SHORT = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ")
        val DAY_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

        fun timeToMinutes(t: String): Int = try {
            val (h, m) = t.split(":").map { it.trim().toInt() }
            h * 60 + m
        } catch (e: Exception) { 0 }
    }

    private fun getRootJson(): JSONObject {
        cachedRoot?.let { return it }
        val raw = cachedJson ?: loadJsonString().also { cachedJson = it }
        val root = JSONObject(raw)
        cachedRoot = root
        return root
    }

    suspend fun refreshFromNetwork(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/data/edupage_schedule.json")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrEmpty() && body.length > 500) {
                    cachedJson = body
                    cachedRoot = JSONObject(body)
                    try { getCachedFile().writeText(body) } catch (e: Exception) {}
                    return@withContext true
                }
            }
        } catch (e: Exception) {}
        false
    }

    fun getAvailableGroups(): List<String> {
        return try {
            val root = getRootJson()
            val list = mutableListOf<String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.length > 2 && !k.startsWith("-")) {
                    list.add(k)
                }
            }
            list.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAvailableTeachers(): List<String> {
        return try {
            val root = getRootJson()
            val set = sortedSetOf<String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val groupObj = root.optJSONObject(keys.next()) ?: continue
                for (w in listOf("odd", "even")) {
                    val weekObj = groupObj.optJSONObject(w) ?: continue
                    for (d in 0..5) {
                        val dayObj = weekObj.optJSONObject(d.toString()) ?: continue
                        for (p in 0..7) {
                            val slot = dayObj.optJSONObject(p.toString()) ?: continue
                            val teacher = slot.optString("teacher", "").trim()
                            if (teacher.isNotEmpty()) {
                                teacher.split(",").forEach { part ->
                                    val cleaned = part.trim()
                                    if (cleaned.length > 3) set.add(cleaned)
                                }
                            }
                        }
                    }
                }
            }
            set.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAvailableRooms(): List<String> {
        return try {
            val root = getRootJson()
            val set = sortedSetOf<String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val groupObj = root.optJSONObject(keys.next()) ?: continue
                for (w in listOf("odd", "even")) {
                    val weekObj = groupObj.optJSONObject(w) ?: continue
                    for (d in 0..5) {
                        val dayObj = weekObj.optJSONObject(d.toString()) ?: continue
                        for (p in 0..7) {
                            val slot = dayObj.optJSONObject(p.toString()) ?: continue
                            val room = slot.optString("room", "").trim()
                            if (room.isNotEmpty()) {
                                room.split(",").forEach { part ->
                                    val cleaned = part.trim()
                                    if (cleaned.isNotEmpty()) set.add(cleaned)
                                }
                            }
                        }
                    }
                }
            }
            set.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveNextTargetLesson(
        targetType: String,
        targetName: String,
        mode: String = "next"
    ): NextLessonResult = withContext(Dispatchers.IO) {
        refreshFromNetwork()
        val root = getRootJson()

        val actualTargetName = if (targetName.isBlank()) {
            val userSavedGroup = context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE).getString("user_group", null)
            userSavedGroup ?: getAvailableGroups().firstOrNull() ?: ""
        } else {
            targetName
        }

        val cal = Calendar.getInstance()
        val nowDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val currentDayIdx = when (nowDayOfWeek) {
            Calendar.MONDAY    -> 0
            Calendar.TUESDAY   -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY  -> 3
            Calendar.FRIDAY    -> 4
            Calendar.SATURDAY  -> 5
            else               -> 6 // Sunday
        }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val weekType = if (cal.get(Calendar.WEEK_OF_YEAR) % 2 == 0) "even" else "odd"

        val badge = when (targetType) {
            "teacher" -> if (actualTargetName.length > 18) actualTargetName.take(16) + "…" else actualTargetName
            "room"    -> "Ауд. $actualTargetName"
            else      -> actualTargetName
        }

        // 1. Check today's lessons if today is a study day (0..5)
        if (currentDayIdx in 0..5) {
            val todayLessons = getLessonsForDay(root, targetType, actualTargetName, currentDayIdx, weekType)
            if (todayLessons.isNotEmpty()) {
                // If user wants "current" mode (or if mode == "current"), check for ongoing lesson first
                if (mode == "current") {
                    for (l in todayLessons) {
                        val start = timeToMinutes(l.startTime)
                        val end   = timeToMinutes(l.endTime)
                        if (nowMinutes in start..end) {
                            val left = end - nowMinutes
                            val timeLabel = if (left > 1)
                                "${l.startTime}–${l.endTime} · осталось $left мин"
                            else
                                "${l.startTime}–${l.endTime}"
                            return@withContext NextLessonResult(
                                lesson = l,
                                statusTag = "ТЕКУЩАЯ ПАРА",
                                timeLabel = timeLabel,
                                isToday = true,
                                targetBadge = badge
                            )
                        }
                    }
                }

                // Look for next upcoming lesson today
                for (l in todayLessons) {
                    val start = timeToMinutes(l.startTime)
                    if (start > nowMinutes) {
                        val wait = start - nowMinutes
                        val timeLabel = if (wait <= 90)
                            "${l.startTime}–${l.endTime} · через $wait мин"
                        else
                            "${l.startTime}–${l.endTime}"
                        return@withContext NextLessonResult(
                            lesson = l,
                            statusTag = "СЛЕДУЮЩАЯ ПАРА",
                            timeLabel = timeLabel,
                            isToday = true,
                            targetBadge = badge
                        )
                    }
                }

                // If mode was "next" but there are no further upcoming lessons today,
                // and there is an ongoing lesson right now, show ongoing as fallback
                if (mode == "next") {
                    for (l in todayLessons) {
                        val start = timeToMinutes(l.startTime)
                        val end   = timeToMinutes(l.endTime)
                        if (nowMinutes in start..end) {
                            val left = end - nowMinutes
                            val timeLabel = if (left > 1)
                                "${l.startTime}–${l.endTime} · осталось $left мин"
                            else
                                "${l.startTime}–${l.endTime}"
                            return@withContext NextLessonResult(
                                lesson = l,
                                statusTag = "ТЕКУЩАЯ ПАРА",
                                timeLabel = timeLabel,
                                isToday = true,
                                targetBadge = badge
                            )
                        }
                    }
                }
            }
        }

        // 2. No lessons left today or today is Sunday/free day → Look ahead to next days (1..7)
        for (offset in 1..7) {
            val nextDayIdx = (currentDayIdx + offset) % 7
            if (nextDayIdx >= 6) continue // Skip Sunday
            val nextLessons = getLessonsForDay(root, targetType, actualTargetName, nextDayIdx, weekType)
            if (nextLessons.isNotEmpty()) {
                val firstLesson = nextLessons.first()
                val dayShort = DAY_SHORT.getOrElse(nextDayIdx) { "" }
                return@withContext NextLessonResult(
                    lesson = firstLesson,
                    statusTag = "СЛЕДУЮЩАЯ ПАРА",
                    timeLabel = if (dayShort.isNotEmpty()) "$dayShort · ${firstLesson.startTime}–${firstLesson.endTime}" else "${firstLesson.startTime}–${firstLesson.endTime}",
                    isToday = false,
                    targetBadge = badge
                )
            }
        }

        // 3. Fallback: absolutely no lessons found anywhere for this target in database
        NextLessonResult(
            lesson = null,
            statusTag = "НЕТ ПАР",
            timeLabel = "",
            isToday = false,
            targetBadge = badge
        )
    }

    private fun getLessonsForDay(
        root: JSONObject,
        targetType: String,
        targetName: String,
        dayIdx: Int,
        weekType: String
    ): List<Lesson> {
        val list = mutableListOf<Lesson>()
        when (targetType) {
            "teacher" -> {
                val map = mutableMapOf<Int, Lesson>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val gName = keys.next()
                    val groupObj = root.optJSONObject(gName) ?: continue
                    val weekObj = groupObj.optJSONObject(weekType) ?: groupObj.optJSONObject("odd") ?: continue
                    val dayObj = weekObj.optJSONObject(dayIdx.toString()) ?: continue

                    for (slotIdx in 0..7) {
                        val slot = dayObj.optJSONObject(slotIdx.toString()) ?: continue
                        val tStr = slot.optString("teacher", "")
                        if (tStr.contains(targetName, ignoreCase = true)) {
                            val times = SLOTS.getOrElse(slotIdx) { Pair("08:00", "09:20") }
                            val existing = map[slotIdx]
                            val combinedGroup = if (existing != null) "${existing.group}, $gName" else gName

                            map[slotIdx] = Lesson(
                                number = (slotIdx + 1).toString(),
                                startTime = times.first,
                                endTime = times.second,
                                subject = slot.optString("subject", ""),
                                teacher = tStr,
                                room = slot.optString("room", ""),
                                type = formatType(slot.optString("type", "")),
                                group = combinedGroup,
                                dayIndex = dayIdx
                            )
                        }
                    }
                }
                list.addAll(map.values)
            }
            "room" -> {
                val map = mutableMapOf<Int, Lesson>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val gName = keys.next()
                    val groupObj = root.optJSONObject(gName) ?: continue
                    val weekObj = groupObj.optJSONObject(weekType) ?: groupObj.optJSONObject("odd") ?: continue
                    val dayObj = weekObj.optJSONObject(dayIdx.toString()) ?: continue

                    for (slotIdx in 0..7) {
                        val slot = dayObj.optJSONObject(slotIdx.toString()) ?: continue
                        val rStr = slot.optString("room", "")
                        if (rStr.contains(targetName, ignoreCase = true)) {
                            val times = SLOTS.getOrElse(slotIdx) { Pair("08:00", "09:20") }
                            val existing = map[slotIdx]
                            val combinedGroup = if (existing != null) "${existing.group}, $gName" else gName

                            map[slotIdx] = Lesson(
                                number = (slotIdx + 1).toString(),
                                startTime = times.first,
                                endTime = times.second,
                                subject = slot.optString("subject", ""),
                                teacher = slot.optString("teacher", ""),
                                room = rStr,
                                type = formatType(slot.optString("type", "")),
                                group = combinedGroup,
                                dayIndex = dayIdx
                            )
                        }
                    }
                }
                list.addAll(map.values)
            }
            else -> { // Group
                var groupObj = root.optJSONObject(targetName)
                if (groupObj == null) {
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k.equals(targetName, ignoreCase = true) || k.contains(targetName, ignoreCase = true)) {
                            groupObj = root.getJSONObject(k)
                            break
                        }
                    }
                }
                if (groupObj != null) {
                    val weekObj = groupObj.optJSONObject(weekType) ?: groupObj.optJSONObject("odd")
                    if (weekObj != null) {
                        val dayObj = weekObj.optJSONObject(dayIdx.toString())
                        if (dayObj != null) {
                            for (slotIdx in 0..7) {
                                val slot = dayObj.optJSONObject(slotIdx.toString()) ?: continue
                                val times = SLOTS.getOrElse(slotIdx) { Pair("08:00", "09:20") }
                                list.add(
                                    Lesson(
                                        number = (slotIdx + 1).toString(),
                                        startTime = times.first,
                                        endTime = times.second,
                                        subject = slot.optString("subject", ""),
                                        teacher = slot.optString("teacher", ""),
                                        room = slot.optString("room", ""),
                                        type = formatType(slot.optString("type", "")),
                                        group = targetName,
                                        dayIndex = dayIdx
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return list.sortedBy { timeToMinutes(it.startTime) }
    }

    private fun formatType(type: String): String = when (type.lowercase()) {
        "lecture" -> "Лекция"
        "lab" -> "Лабораторная"
        "seminar" -> "Семинар"
        "practice" -> "Практика"
        else -> if (type.isNotEmpty()) type else "Пара"
    }

    suspend fun getScheduleForGroup(groupName: String, dayIndex: Int): List<Lesson> = withContext(Dispatchers.IO) {
        val root = getRootJson()
        val cal = Calendar.getInstance()
        val weekType = if (cal.get(Calendar.WEEK_OF_YEAR) % 2 == 0) "even" else "odd"
        getLessonsForDay(root, "group", groupName, dayIndex, weekType)
    }

    suspend fun getScheduleForTeacher(teacherName: String, dayIndex: Int): List<Lesson> = withContext(Dispatchers.IO) {
        val root = getRootJson()
        val cal = Calendar.getInstance()
        val weekType = if (cal.get(Calendar.WEEK_OF_YEAR) % 2 == 0) "even" else "odd"
        getLessonsForDay(root, "teacher", teacherName, dayIndex, weekType)
    }

    suspend fun getScheduleForRoom(roomName: String, dayIndex: Int): List<Lesson> = withContext(Dispatchers.IO) {
        val root = getRootJson()
        val cal = Calendar.getInstance()
        val weekType = if (cal.get(Calendar.WEEK_OF_YEAR) % 2 == 0) "even" else "odd"
        getLessonsForDay(root, "room", roomName, dayIndex, weekType)
    }

    private fun getCachedFile(): java.io.File = java.io.File(context.filesDir, "edupage_schedule_cached.json")

    private fun loadJsonString(): String {
        val cached = getCachedFile()
        if (cached.exists() && cached.length() > 500) {
            try {
                return cached.readText()
            } catch (e: Exception) {}
        }
        return context.assets.open("web/data/edupage_schedule.json").use { input ->
            InputStreamReader(input).readText()
        }
    }
}

