package uz.riat.tdiu.ui.schedule

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import uz.riat.tdiu.R
import uz.riat.tdiu.data.repository.ScheduleRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment() {

    private lateinit var adapter: ScheduleAdapter
    private lateinit var repository: ScheduleRepository
    private val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    private var selectedDay = 0
    private var selectedGroup = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = ScheduleRepository(requireContext())

        val rv = view.findViewById<RecyclerView>(R.id.rvSchedule)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = ScheduleAdapter(emptyList())
        rv.adapter = adapter

        val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        selectedDay = when (currentDayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0
        }

        val tvDay = view.findViewById<TextView>(R.id.tvCurrentDay)
        val sdf = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        tvDay.text = sdf.format(Calendar.getInstance().time).replaceFirstChar { it.uppercase() }

        setupDayTabs(view)
        setupGroupSpinner(view)
        loadSchedule()
    }

    private fun setupDayTabs(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.dayTabsContainer)
        container.removeAllViews()
        dayNames.forEachIndexed { index, name ->
            val tv = TextView(requireContext()).apply {
                text = name
                textSize = 13f
                setTextColor(if (index == selectedDay) Color.WHITE else 0xFF94A3B8.toInt())
                setPadding(32, 18, 32, 18)
                setBackgroundResource(
                    if (index == selectedDay) R.drawable.bg_pill_primary_active
                    else R.drawable.bg_pill_primary
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 4, 0) }
                layoutParams = lp
                setOnClickListener {
                    selectedDay = index
                    setupDayTabs(view)
                    loadSchedule()
                }
            }
            container.addView(tv)
        }
    }

    private fun setupGroupSpinner(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinnerGroup)
        val prefs = requireContext().getSharedPreferences("riat_prefs", 0)
        val groups = repository.getAvailableGroups()
        val savedGroup = prefs.getString("user_group", null)
        selectedGroup = if (savedGroup != null && groups.contains(savedGroup)) {
            savedGroup
        } else {
            groups.firstOrNull() ?: ""
        }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, groups).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = spinnerAdapter
        val initialIdx = groups.indexOf(selectedGroup)
        if (initialIdx >= 0) {
            spinner.setSelection(initialIdx)
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position in groups.indices) {
                    selectedGroup = groups[position]
                    prefs.edit().putString("user_group", selectedGroup).apply()
                    loadSchedule()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadSchedule() {
        val view = view ?: return
        val emptyState = view.findViewById<LinearLayout>(R.id.emptyState)
        val rv = view.findViewById<RecyclerView>(R.id.rvSchedule)
        lifecycleScope.launch {
            val lessons = repository.getScheduleForGroup(selectedGroup, selectedDay)
            adapter.updateData(lessons)
            if (lessons.isEmpty()) {
                rv.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                rv.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
        }
    }
}
