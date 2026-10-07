package uz.riat.tdiu.data.update

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class VersionInfo(
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("changelog") val changelog: String?
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val VERSION_URL = "https://tsue-digital-economy.web.app/update.json"
    private const val PREF_DOWNLOAD_ID = "update_download_id"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkForUpdates(activity: Activity, silent: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url(VERSION_URL)
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    if (!silent) withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Не удалось проверить обновления (${resp.code})", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val bodyStr = resp.body?.string() ?: return@launch
                val info = Gson().fromJson(bodyStr, VersionInfo::class.java)

                val currentCode = try {
                    val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pInfo.versionCode
                    }
                } catch (e: Exception) { 1 }

                Log.d(TAG, "Current versionCode=$currentCode, Remote versionCode=${info.versionCode}")

                if (info.versionCode > currentCode) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(activity, info)
                    }
                } else if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "У вас установлена последняя версия (${info.versionName})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                if (!silent) withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Ошибка проверки: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, info: VersionInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val message = buildString {
            append("Доступна версия ${info.versionName}\n\n")
            if (!info.changelog.isNullOrBlank()) {
                append("Что нового:\n${info.changelog}\n\n")
            }
            append("Обновление скачается в фоне и установится автоматически.")
        }

        AlertDialog.Builder(activity)
            .setTitle("Обновление приложения")
            .setMessage(message)
            .setPositiveButton("Обновить") { _, _ ->
                startSystemDownload(activity, info)
            }
            .setNegativeButton("Позже", null)
            .setCancelable(true)
            .show()
    }

    private fun startSystemDownload(activity: Activity, info: VersionInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(activity)
                    .setTitle("Нужно разрешение")
                    .setMessage("Для установки обновления разрешите этому приложению устанавливать APK. Это безопасно — мы запросим только для нашего приложения.")
                    .setPositiveButton("Открыть настройки") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                        Toast.makeText(activity, "После разрешения вернитесь и проверьте обновления снова", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                return
            }
        }

        val fileName = "RIAT-TDIU-${info.versionName}.apk"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(downloadsDir, fileName)
        if (destFile.exists()) destFile.delete()

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("RIAT-TSUE — Update ${info.versionName}")
            setDescription("Downloading application update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setAllowedOverRoaming(true)
        }

        val downloadId = dm.enqueue(request)

        activity.getSharedPreferences("riat_updates", Context.MODE_PRIVATE)
            .edit().putLong(PREF_DOWNLOAD_ID, downloadId).apply()

        Toast.makeText(activity, "⬇ Обновление скачивается в фоне. Откройте шторку уведомлений.", Toast.LENGTH_LONG).show()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return

                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                var success = false
                var statusMsg = ""

                if (cursor.moveToFirst()) {
                    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    when (cursor.getInt(statusCol)) {
                        DownloadManager.STATUS_SUCCESSFUL -> success = true
                        DownloadManager.STATUS_FAILED -> {
                            val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            statusMsg = "Ошибка загрузки (код ${cursor.getInt(reasonCol)})"
                        }
                        else -> statusMsg = "Загрузка прервана"
                    }
                }
                cursor.close()

                if (success) {
                    val apkUri = FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.fileprovider",
                        destFile
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    ctx.startActivity(installIntent)
                } else {
                    if (activity.isFinishing || activity.isDestroyed) return
                    activity.runOnUiThread {
                        Toast.makeText(activity, statusMsg.ifEmpty { "Загрузка завершилась с ошибкой" }, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(receiver, filter)
        }

        Log.d(TAG, "Download enqueued id=$downloadId -> $fileName")
    }
}
