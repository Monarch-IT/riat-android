package uz.riat.tdiu.ui.schedule

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import uz.riat.tdiu.R
import uz.riat.tdiu.data.model.Lesson

class ScheduleAdapter(private var items: List<Lesson>) :
    RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tvLessonNumber)
        val tvStart: TextView = itemView.findViewById(R.id.tvStartTime)
        val tvEnd: TextView = itemView.findViewById(R.id.tvEndTime)
        val tvSubject: TextView = itemView.findViewById(R.id.tvSubject)
        val tvType: TextView = itemView.findViewById(R.id.tvLessonType)
        val tvTeacher: TextView = itemView.findViewById(R.id.tvTeacher)
        val tvRoom: TextView = itemView.findViewById(R.id.tvRoom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lesson = items[position]
        holder.tvNumber.text = lesson.number
        holder.tvStart.text = lesson.startTime
        holder.tvEnd.text = lesson.endTime
        holder.tvSubject.text = lesson.subject
        holder.tvTeacher.text = lesson.teacher
        holder.tvRoom.text = lesson.room

        holder.tvType.text = lesson.type
        val (bgRes, textColorRes) = when (lesson.type.lowercase()) {
            "практика" -> Pair(R.drawable.bg_tag_practice, R.color.tag_practice_text)
            "лабораторная", "лаб" -> Pair(R.drawable.bg_tag_lab, R.color.tag_lab_text)
            "семинар" -> Pair(R.drawable.bg_tag_seminar, R.color.tag_seminar_text)
            else -> Pair(R.drawable.bg_tag_lecture, R.color.tag_lecture_text)
        }
        holder.tvType.setBackgroundResource(bgRes)
        holder.tvType.setTextColor(ContextCompat.getColor(holder.itemView.context, textColorRes))
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Lesson>) {
        items = newItems
        notifyDataSetChanged()
    }
}
