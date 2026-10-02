package uz.riat.tdiu.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import uz.riat.tdiu.R
import uz.riat.tdiu.data.notification.ScheduleAlarmScheduler
import uz.riat.tdiu.data.repository.ScheduleRepository
import java.util.Locale

class NotificationConfigActivity : AppCompatActivity() {

    private var selectedAdvanceMin = 15
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_settings)

        val widgetPrefs = getSharedPreferences("riat_widget_prefs", Context.MODE_PRIVATE)
        val riatPrefs = getSharedPreferences("riat_prefs", Context.MODE_PRIVATE)
        val targetType = widgetPrefs.getString("active_target_type", "group") ?: "group"
        val targetName = widgetPrefs.getString("active_target_name", "")
            ?.ifEmpty { null }
            ?: riatPrefs.getString("user_group", "")
            ?.ifEmpty { null }
            ?: ScheduleRepository(this).getAvailableGroups().firstOrNull() ?: ""

        val typeLabel = when (targetType) {
            "teacher" -> "Преподаватель"
            "room"    -> "Аудитория"
            else      -> "Группа"
        }
        val displayBadge = if (targetName.isNotEmpty()) "$typeLabel: $targetName" else "Расписание"
        findViewById<TextView>(R.id.tvNotifyTargetBadge).text = displayBadge

        findViewById<ImageView>(R.id.btnCloseNotifySettings).setOnClickListener {
            finish()
        }

        val notifyPrefs = getSharedPreferences("riat_notify_prefs", Context.MODE_PRIVATE)
        val switchMaster = findViewById<SwitchCompat>(R.id.switchEnableNotifications)
        val switchSound   = findViewById<SwitchCompat>(R.id.switchSound)
        val switchVibrate = findViewById<SwitchCompat>(R.id.switchVibrate)
        val switchVoice   = findViewById<SwitchCompat>(R.id.switchVoice)

        switchMaster.isChecked = notifyPrefs.getBoolean("notify_enabled", true)
        switchSound.isChecked   = notifyPrefs.getBoolean("notify_sound", true)
        switchVibrate.isChecked = notifyPrefs.getBoolean("notify_vibrate", true)
        switchVoice.isChecked   = notifyPrefs.getBoolean("notify_voice", true)

        selectedAdvanceMin = notifyPrefs.getInt("notify_advance_min", 15)

        setupAdvanceTimeButtons()
        setupTtsTester(typeLabel, targetName)
        setupSaveButton(switchMaster, switchSound, switchVibrate, switchVoice)
    }

    private fun setupAdvanceTimeButtons() {
        val b5  = findViewById<TextView>(R.id.btnTime5)
        val b10 = findViewById<TextView>(R.id.btnTime10)
        val b15 = findViewById<TextView>(R.id.btnTime15)
        val b30 = findViewById<TextView>(R.id.btnTime30)

        fun select(active: TextView, min: Int, vararg rest: TextView) {
            selectedAdvanceMin = min
            active.setBackgroundResource(R.drawable.bg_card)
            active.backgroundTintList = ColorStateList.valueOf(0xFF004899.toInt())
            active.setTextColor(0xFFFFFFFF.toInt())
            rest.forEach {
                it.setBackgroundResource(R.drawable.bg_search_input_light)
                it.backgroundTintList = null
                it.setTextColor(0xFF475569.toInt())
            }
        }

        b5.setOnClickListener  { select(b5, 5, b10, b15, b30) }
        b10.setOnClickListener { select(b10, 10, b5, b15, b30) }
        b15.setOnClickListener { select(b15, 15, b5, b10, b30) }
        b30.setOnClickListener { select(b30, 30, b5, b10, b15) }

        when (selectedAdvanceMin) {
            5    -> select(b5, 5, b10, b15, b30)
            10   -> select(b10, 10, b5, b15, b30)
            30   -> select(b30, 30, b5, b10, b15)
            else -> select(b15, 15, b5, b10, b30)
        }
    }

    private fun setupTtsTester(typeLabel: String, targetName: String) {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("ru", "RU"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }

        findViewById<LinearLayout>(R.id.btnTestVoice).setOnClickListener {
            val sampleText = if (targetName.isNotEmpty()) {
                "Внимание! Через $selectedAdvanceMin минут начинается пара по расписанию для $targetName."
            } else {
                "Внимание! Через $selectedAdvanceMin минут начинается пара."
            }
            tts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "TEST_TTS")
            Toast.makeText(this, "Воспроизведение голосового напоминания…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSaveButton(
        switchMaster: SwitchCompat,
        switchSound: SwitchCompat,
        switchVibrate: SwitchCompat,
        switchVoice: SwitchCompat
    ) {
        findViewById<MaterialButton>(R.id.btnSaveNotifications).setOnClickListener {
            getSharedPreferences("riat_notify_prefs", Context.MODE_PRIVATE).edit().apply {
                putBoolean("notify_enabled",     switchMaster.isChecked)
                putInt    ("notify_advance_min", selectedAdvanceMin)
                putBoolean("notify_sound",       switchSound.isChecked)
                putBoolean("notify_vibrate",     switchVibrate.isChecked)
                putBoolean("notify_voice",       switchVoice.isChecked)
                apply()
            }

            if (switchMaster.isChecked) {
                ScheduleAlarmScheduler.scheduleNextAlarm(this)
                Toast.makeText(this, "Напоминания включены (за $selectedAdvanceMin мин)", Toast.LENGTH_SHORT).show()
            } else {
                ScheduleAlarmScheduler.cancelAlarms(this)
                Toast.makeText(this, "Напоминания выключены", Toast.LENGTH_SHORT).show()
            }

            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}
