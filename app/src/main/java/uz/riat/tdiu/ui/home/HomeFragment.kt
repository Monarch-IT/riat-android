package uz.riat.tdiu.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import uz.riat.tdiu.R
import uz.riat.tdiu.data.repository.FacultyRepository
import uz.riat.tdiu.data.repository.ScheduleRepository
import uz.riat.tdiu.ui.news.NewsAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var scheduleRepo: ScheduleRepository
    private lateinit var facultyRepo: FacultyRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scheduleRepo = ScheduleRepository(requireContext())
        facultyRepo = FacultyRepository(requireContext())

        setupGreeting(view)
        loadNextLesson(view)
        loadNews(view)
    }

    private fun setupGreeting(view: View) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Доброе утро"
            hour < 18 -> "Добрый день"
            else -> "Добрый вечер"
        }
        val sdf = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        val dateStr = sdf.format(Calendar.getInstance().time).replaceFirstChar { it.uppercase() }
        view.findViewById<TextView>(R.id.tvGreeting).text = greeting
        view.findViewById<TextView>(R.id.tvDate).text = dateStr
    }

    private fun loadNextLesson(view: View) {
        val prefs = requireContext().getSharedPreferences("riat_prefs", 0)
        val group = prefs.getString("user_group", null)
            ?: scheduleRepo.getAvailableGroups().firstOrNull() ?: ""
        view.findViewById<TextView>(R.id.tvUserGroup).text = group

        lifecycleScope.launch {
            val result = scheduleRepo.resolveNextTargetLesson("group", group)
            val card = view.findViewById<View>(R.id.cardNextLesson)
            val lesson = result.lesson
            if (lesson != null) {
                view.findViewById<TextView>(R.id.tvNextTime).text = result.timeLabel
                view.findViewById<TextView>(R.id.tvNextSubject).text = lesson.subject
                view.findViewById<TextView>(R.id.tvNextTeacher).text = lesson.teacher
                view.findViewById<TextView>(R.id.tvNextRoom).text = if (lesson.room.startsWith("Ауд", ignoreCase = true)) lesson.room else "Ауд. ${lesson.room}"
                view.findViewById<TextView>(R.id.tvNextType).text = "${result.statusTag} · ${lesson.type}"
                card.visibility = View.VISIBLE
            } else {
                card.visibility = View.GONE
            }
        }
    }

    private fun loadNews(view: View) {
        lifecycleScope.launch {
            val news = facultyRepo.getNews().take(3)
            val rv = view.findViewById<RecyclerView>(R.id.rvHomeNews)
            rv.layoutManager = LinearLayoutManager(requireContext())
            rv.adapter = NewsAdapter(news) { }
            rv.isNestedScrollingEnabled = false
        }
    }
}
