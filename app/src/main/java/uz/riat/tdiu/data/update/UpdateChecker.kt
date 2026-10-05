package uz.riat.tdiu.data.update

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import java.io.FileOutputStream
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkForUpdates(activity: Activity, silent: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url(VERSION_URL)
                    .header("Cache-Control", "no-cache")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "Не удалось проверить обновления", Toast.LENGTH_SHORT).show()
                        }
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
                } catch (e: Exception) {
                    1
                }

                Log.d(TAG, "Current versionCode=$currentCode, Remote versionCode=${info.versionCode}")

                if (info.versionCode > currentCode) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(activity, info)
                    }
                } else if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "У вас установлена последняя версия", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Ошибка проверки: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, info: VersionInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val message = buildString {
            append("Доступна новая версия: ${info.versionName}\n\n")
            if (!info.changelog.isNullOrBlank()) {
                append("Что нового:\n${info.changelog}\n\n")
            }
            append("Хотите обновить приложение прямо сейчас?")
        }

        AlertDialog.Builder(activity)
            .setTitle("Обновление приложения")
            .setMessage(message)
            .setPositiveButton("Обновить") { _, _ ->
                downloadAndInstall(activity, info.apkUrl)
            }
            .setNegativeButton("Позже", null)
            .setCancelable(true)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, apkUrl: String) {
        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Загрузка обновления")
            setMessage("Скачивание APK…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder().url(apkUrl).build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    throw Exception("HTTP ${resp.code}")
                }

                val body = resp.body ?: throw Exception("Пустой ответ сервера")
                val totalBytes = body.contentLength()

                val cacheDir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        withContext(Dispatchers.Main) {
                            progressDialog.progress = progress
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    promptInstall(activity, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(activity, "Ошибка загрузки: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun promptInstall(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    Toast.makeText(activity, "Разрешите установку из этого источника и повторите", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(activity, "Не удалось запустить установщик: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
