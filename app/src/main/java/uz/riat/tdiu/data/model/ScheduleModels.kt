package uz.riat.tdiu.data.model

data class Lesson(
    val number: String,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val teacher: String,
    val room: String,
    val type: String,
    val group: String,
    val dayIndex: Int = 0
)
