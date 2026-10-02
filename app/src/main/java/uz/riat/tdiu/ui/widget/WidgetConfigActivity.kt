package uz.riat.tdiu.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.riat.tdiu.R
import uz.riat.tdiu.data.notification.ScheduleAlarmScheduler
import uz.riat.tdiu.data.repository.ScheduleRepository

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // Active selection state
    private var selectedCategory   = "group" // "group", "teacher", "room"
    private var selectedTargetName = ""
    private var selectedTheme      = "light"
    private var selectedOpacity    = 100

    // Datasets
    private var allGroups   = listOf<String>()
    private var allTeachers = listOf<String>()
    private var allRooms    = listOf<String>()
    private var filteredList = mutableListOf<String>()

    // Views
    private lateinit var previewCard:      RelativeLayout
    private lateinit var tvPreviewGroup:   TextView
    private lateinit var tvPreviewTag:     TextView
    private lateinit var tvPreviewSubject: TextView
    private lateinit var tvPreviewDetails: TextView
    private lateinit var tvPreviewTime:    TextView
    private lateinit var tvPreviewType:    TextView
    private lateinit var btnPreviewNotify: ImageButton

    private lateinit var tabGroup:   TextView
    private lateinit var tabTeacher: TextView
    private lateinit var tabRoom:    TextView
    private lateinit var etSearch:   EditText
    private lateinit var tvSelectedInfo: TextView
    private lateinit var lvResults:  ListView
    private lateinit var adapter:    TargetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val mgr = AppWidgetManager.getInstance(this)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(this, ScheduleWidgetProvider::class.java))
            appWidgetId = if (ids.isNotEmpty()) ids[0] else 0
        }

        // Restore previously saved state if editing
        val prefs = getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
        val riatPrefs = getSharedPreferences("riat_prefs", Context.MODE_PRIVATE)
        selectedCategory = prefs.getString("widget_target_type_$appWidgetId", "group") ?: "group"
        selectedTargetName = prefs.getString("widget_target_name_$appWidgetId", "")
            ?.ifEmpty { null }
            ?: prefs.getString("widget_group_$appWidgetId", "")
            ?.ifEmpty { null }
            ?: riatPrefs.getString("user_group", "") ?: ""

        val isDarkApp = riatPrefs.getBoolean("dark_mode", false)
        val defaultTheme = if (isDarkApp) "dark" else "light"
        selectedTheme = prefs.getString("widget_theme_$appWidgetId", defaultTheme) ?: defaultTheme
        selectedOpacity = prefs.getInt("widget_opacity_$appWidgetId", 100)

        bindViews()
        setupSearchAndList()
        setupCategoryTabs()
        setupThemeSelector()
        setupOpacitySlider()
        setupSaveButton()

        loadScheduleData()
        syncPreview()
    }

    private fun bindViews() {
        previewCard      = findViewById(R.id.widgetPreviewCard)
        tvPreviewTag     = findViewById(R.id.tvPreviewTag)
        tvPreviewSubject = findViewById(R.id.tvPreviewSubject)
        tvPreviewDetails = findViewById(R.id.tvPreviewDetails)
        tvPreviewTime    = findViewById(R.id.tvPreviewTime)
        tvPreviewGroup   = findViewById(R.id.tvPreviewGroup)
        tvPreviewType    = findViewById(R.id.tvPreviewType)
        btnPreviewNotify = findViewById(R.id.btnPreviewNotify)

        tabGroup       = findViewById(R.id.tabCategoryGroup)
        tabTeacher     = findViewById(R.id.tabCategoryTeacher)
        tabRoom        = findViewById(R.id.tabCategoryRoom)
        etSearch       = findViewById(R.id.etSearchTarget)
        tvSelectedInfo = findViewById(R.id.tvSelectedTargetInfo)
        lvResults      = findViewById(R.id.lvTargetResults)

        btnPreviewNotify.setOnClickListener {
            val i = Intent(this, NotificationConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivity(i)
        }
    }

    private fun setupCategoryTabs() {
        fun selectTab(active: TextView, cat: String, vararg rest: TextView) {
            selectedCategory = cat
            active.setBackgroundResource(R.drawable.bg_card)
            active.backgroundTintList = ColorStateList.valueOf(0xFF004899.toInt())
            active.setTextColor(0xFFFFFFFF.toInt())
            rest.forEach {
                it.setBackgroundResource(R.drawable.bg_search_input_light)
                it.backgroundTintList = null
                it.setTextColor(0xFF475569.toInt())
            }
            etSearch.text.clear()
            updateFilteredList("")
            if (filteredList.isNotEmpty() && !filteredList.contains(selectedTargetName)) {
                selectedTargetName = filteredList[0]
                updateSelectionUI()
                loadPreviewForTarget()
            }
        }

        tabGroup.setOnClickListener   { selectTab(tabGroup, "group", tabTeacher, tabRoom) }
        tabTeacher.setOnClickListener { selectTab(tabTeacher, "teacher", tabGroup, tabRoom) }
        tabRoom.setOnClickListener    { selectTab(tabRoom, "room", tabGroup, tabTeacher) }

        when (selectedCategory) {
            "teacher" -> selectTab(tabTeacher, "teacher", tabGroup, tabRoom)
            "room"    -> selectTab(tabRoom, "room", tabGroup, tabTeacher)
            else      -> selectTab(tabGroup, "group", tabTeacher, tabRoom)
        }
    }

    private fun setupSearchAndList() {
        adapter = TargetAdapter()
        lvResults.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateFilteredList(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lvResults.setOnItemClickListener { _, _, position, _ ->
            if (position in filteredList.indices) {
                selectedTargetName = filteredList[position]
                updateSelectionUI()
                adapter.notifyDataSetChanged()
                loadPreviewForTarget()
            }
        }

        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        val catName = when (selectedCategory) {
            "teacher" -> "Преподаватель"
            "room"    -> "Аудитория"
            else      -> "Группа"
        }
        val display = if (selectedTargetName.isNotEmpty()) selectedTargetName else "Не выбрано"
        tvSelectedInfo.text = "Выбрано: [$catName] $display"
        tvPreviewGroup.text = display
    }

    private fun loadScheduleData() {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = ScheduleRepository(this@WidgetConfigActivity)
            allGroups   = repo.getAvailableGroups()
            allTeachers = repo.getAvailableTeachers()
            allRooms    = repo.getAvailableRooms()

            if (selectedTargetName.isEmpty()) {
                selectedTargetName = when (selectedCategory) {
                    "teacher" -> allTeachers.firstOrNull() ?: ""
                    "room"    -> allRooms.firstOrNull() ?: ""
                    else      -> allGroups.firstOrNull() ?: ""
                }
            }

            withContext(Dispatchers.Main) {
                updateSelectionUI()
                updateFilteredList("")
                loadPreviewForTarget()
            }
        }
    }

    private fun updateFilteredList(query: String) {
        val source = when (selectedCategory) {
            "teacher" -> allTeachers
            "room"    -> allRooms
            else      -> allGroups
        }
        filteredList.clear()
        val q = query.trim()
        if (q.isBlank()) {
            filteredList.addAll(source)
        } else {
            filteredList.addAll(source.filter { it.contains(q, ignoreCase = true) })
        }
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadPreviewForTarget() {
        if (selectedTargetName.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val repo = ScheduleRepository(this@WidgetConfigActivity)
            val result = repo.resolveNextTargetLesson(selectedCategory, selectedTargetName)
            withContext(Dispatchers.Main) {
                val lesson = result.lesson
                if (lesson != null) {
                    tvPreviewTag.text = result.statusTag
                    tvPreviewSubject.text = lesson.subject

                    val detailParts = mutableListOf<String>()
                    if (selectedCategory != "room" && lesson.room.isNotEmpty()) {
                        detailParts.add(if (lesson.room.startsWith("Ауд", ignoreCase = true)) lesson.room else "Ауд. ${lesson.room}")
                    }
                    if (selectedCategory != "teacher" && lesson.teacher.isNotEmpty()) {
                        detailParts.add(lesson.teacher)
                    }
                    if (selectedCategory != "group" && lesson.group.isNotEmpty()) {
                        detailParts.add(lesson.group)
                    }
                    tvPreviewDetails.text = detailParts.joinToString(" · ")
                    tvPreviewTime.text    = result.timeLabel
                    tvPreviewType.text    = lesson.type
                    tvPreviewGroup.text   = result.targetBadge
                } else {
                    tvPreviewTag.text     = "НЕТ ПАР"
                    tvPreviewSubject.text = "Расписание: $selectedTargetName"
                    tvPreviewDetails.text = "Занятия не найдены в базе данных"
                    tvPreviewTime.text    = ""
                    tvPreviewType.text    = ""
                    tvPreviewGroup.text   = selectedTargetName
                }
            }
        }
    }

    private fun setupThemeSelector() {
        val btnDark  = findViewById<TextView>(R.id.themeDark)
        val btnLight = findViewById<TextView>(R.id.themeLight)
        val btnOled  = findViewById<TextView>(R.id.themeOled)

        fun activate(active: TextView, vararg rest: TextView) {
            active.setBackgroundResource(R.drawable.bg_card)
            active.backgroundTintList = ColorStateList.valueOf(0xFF004899.toInt())
            active.setTextColor(0xFFFFFFFF.toInt())
            rest.forEach {
                it.setBackgroundResource(R.drawable.bg_search_input_light)
                it.backgroundTintList = null
                it.setTextColor(0xFF475569.toInt())
            }
        }

        btnDark.setOnClickListener  { selectedTheme = "dark";  activate(btnDark, btnLight, btnOled);  syncPreview() }
        btnLight.setOnClickListener { selectedTheme = "light"; activate(btnLight, btnDark, btnOled);  syncPreview() }
        btnOled.setOnClickListener  { selectedTheme = "oled";  activate(btnOled, btnDark, btnLight);  syncPreview() }

        when (selectedTheme) {
            "dark" -> activate(btnDark, btnLight, btnOled)
            "oled" -> activate(btnOled, btnDark, btnLight)
            else   -> activate(btnLight, btnDark, btnOled)
        }
    }

    private fun setupOpacitySlider() {
        val seek  = findViewById<SeekBar>(R.id.seekOpacity)
        val tvVal = findViewById<TextView>(R.id.tvOpacityValue)
        seek.progress = selectedOpacity
        tvVal.text    = "$selectedOpacity%"

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedOpacity = progress
                tvVal.text = "$progress%"
                syncPreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun syncPreview() {
        val bgRes = when (selectedTheme) {
            "light" -> R.drawable.bg_widget_glass_light
            "oled"  -> opacityDrawableOled(selectedOpacity)
            else    -> opacityDrawableDark(selectedOpacity)
        }
        previewCard.setBackgroundResource(bgRes)

        val isDark = selectedTheme != "light"
        tvPreviewSubject.setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF0F172A.toInt())
        tvPreviewDetails.setTextColor(if (isDark) 0xFF94A3B8.toInt() else 0xFF475569.toInt())
        tvPreviewTime.setTextColor   (0xFF64748B.toInt())
        tvPreviewTag.setTextColor    (if (selectedTheme == "light") 0xFF004899.toInt() else 0xFF00E1D9.toInt())
        tvPreviewGroup.setTextColor  (if (selectedTheme == "light") 0xFF004899.toInt() else 0xFF60A5FA.toInt())
        tvPreviewType.setTextColor   (0xFF475569.toInt())
    }

    private fun opacityDrawableDark(opacity: Int): Int = when {
        opacity >= 90 -> R.drawable.bg_widget_dark_100
        opacity >= 70 -> R.drawable.bg_widget_dark_80
        opacity >= 50 -> R.drawable.bg_widget_dark_60
        opacity >= 30 -> R.drawable.bg_widget_dark_40
        else          -> R.drawable.bg_widget_dark_20
    }

    private fun opacityDrawableOled(opacity: Int): Int = when {
        opacity >= 90 -> R.drawable.bg_widget_oled_100
        opacity >= 70 -> R.drawable.bg_widget_oled_80
        opacity >= 50 -> R.drawable.bg_widget_oled_60
        opacity >= 30 -> R.drawable.bg_widget_oled_40
        else          -> R.drawable.bg_widget_oled_20
    }

    private fun setupSaveButton() {
        findViewById<MaterialButton>(R.id.btnSaveWidget).setOnClickListener {
            getSharedPreferences("riat_widget_prefs", MODE_PRIVATE).edit().apply {
                putString("widget_target_type_$appWidgetId", selectedCategory)
                putString("widget_target_name_$appWidgetId", selectedTargetName)
                putString("widget_group_$appWidgetId",       selectedTargetName)
                putString("widget_theme_$appWidgetId",       selectedTheme)
                putInt   ("widget_opacity_$appWidgetId",     selectedOpacity)
                putString("active_target_type",              selectedCategory)
                putString("active_target_name",              selectedTargetName)
                apply()
            }

            if (selectedCategory == "group" && selectedTargetName.isNotEmpty()) {
                getSharedPreferences("riat_prefs", MODE_PRIVATE).edit()
                    .putString("user_group", selectedTargetName)
                    .apply()
            }

            ScheduleWidgetProvider.updateAppWidget(
                this,
                AppWidgetManager.getInstance(this),
                appWidgetId
            )

            ScheduleAlarmScheduler.scheduleNextAlarm(this)

            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    inner class TargetAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredList.size
        override fun getItem(position: Int): Any = filteredList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.item_target_select, parent, false)

            val item = filteredList[position]
            val tvTitle = view.findViewById<TextView>(R.id.tvTargetItemTitle)
            val tvSub   = view.findViewById<TextView>(R.id.tvTargetItemSub)
            val ivCheck = view.findViewById<ImageView>(R.id.ivTargetSelectedCheck)

            tvTitle.text = item
            tvSub.text = when (selectedCategory) {
                "teacher" -> "Преподаватель факультета"
                "room"    -> "Аудитория кампуса"
                else      -> "Группа факультета"
            }

            val isSelected = item == selectedTargetName
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            ivCheck.imageTintList = ColorStateList.valueOf(0xFF004899.toInt())

            tvTitle.setTextColor(if (isSelected) 0xFF004899.toInt() else 0xFF0F172A.toInt())
            tvSub.setTextColor(0xFF64748B.toInt())
            view.setBackgroundColor(if (isSelected) 0x14004899 else 0x00000000)

            return view
        }
    }
}
