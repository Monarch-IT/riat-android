package uz.riat.tdiu.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import uz.riat.tdiu.R
import uz.riat.tdiu.data.notification.ScheduleAlarmScheduler
import uz.riat.tdiu.data.repository.ScheduleRepository
import uz.riat.tdiu.ui.widget.ScheduleWidgetProvider
import uz.riat.tdiu.ui.widget.WidgetConfigActivity

class SettingsFragment : Fragment() {

    private lateinit var scheduleRepo: ScheduleRepository
    private var allGroups: List<String> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        scheduleRepo = ScheduleRepository(context)
        allGroups = scheduleRepo.getAvailableGroups()

        val prefs = context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE)
        val tvGroup = view.findViewById<TextView>(R.id.tvSelectedGroup)
        val currentGroup = prefs.getString("user_group", null)
            ?: allGroups.firstOrNull()
            ?: ""

        if (currentGroup.isNotEmpty() && prefs.getString("user_group", null) == null) {
            prefs.edit().putString("user_group", currentGroup).apply()
        }

        tvGroup.text = currentGroup

        val switchNotif = view.findViewById<SwitchCompat>(R.id.switchNotifications)
        switchNotif.isChecked = prefs.getBoolean("notifications_enabled", true)
        switchNotif.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notifications_enabled", checked).apply()
        }

        view.findViewById<View>(R.id.rowGroup).setOnClickListener {
            showGroupPicker(view, tvGroup)
        }

        view.findViewById<LinearLayout>(R.id.rowWidgetConfig).setOnClickListener {
            val intent = Intent(requireContext(), WidgetConfigActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showGroupPicker(view: View, tvGroup: TextView) {
        val context = requireContext()
        val groups = if (allGroups.isNotEmpty()) allGroups else scheduleRepo.getAvailableGroups()

        val dialogView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null, false)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val etSearch = EditText(context).apply {
            hint = "Поиск группы (всего: ${groups.size})…"
            setSingleLine(true)
            setBackgroundResource(R.drawable.bg_search_input_light)
            setPadding(32, 24, 32, 24)
            setTextColor(0xFF0F172A.toInt())
            setHintTextColor(0xFF94A3B8.toInt())
            textSize = 14f
        }
        container.addView(etSearch)

        val filteredGroups = groups.toMutableList()
        val listAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, filteredGroups)
        val listView = ListView(context).apply {
            adapter = listAdapter
            divider = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                800
            ).apply { topMargin = 20 }
        }
        container.addView(listView)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Выберите вашу группу")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .create()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty().trim()
                filteredGroups.clear()
                if (q.isEmpty()) {
                    filteredGroups.addAll(groups)
                } else {
                    filteredGroups.addAll(groups.filter { it.contains(q, ignoreCase = true) })
                }
                listAdapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in filteredGroups.indices) {
                val selected = filteredGroups[position]
                context.getSharedPreferences("riat_prefs", Context.MODE_PRIVATE)
                    .edit().putString("user_group", selected).apply()

                context.getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("active_target_type", "group")
                    .putString("active_target_name", selected)
                    .apply()

                tvGroup.text = selected

                ScheduleWidgetProvider.updateAllWidgets(context)
                ScheduleAlarmScheduler.scheduleNextAlarm(context)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
