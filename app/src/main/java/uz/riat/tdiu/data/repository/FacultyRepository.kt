package uz.riat.tdiu.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import uz.riat.tdiu.data.model.Department
import uz.riat.tdiu.data.model.FacultyData
import uz.riat.tdiu.data.model.Leader
import uz.riat.tdiu.data.model.NewsItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FacultyRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val BASE_URL = "https://tsue-digital-economy.web.app"

    suspend fun getFacultyData(): FacultyData = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$BASE_URL/faculty_curated.json").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyFaculty()
                return@withContext parseFaculty(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyFaculty()
    }

    suspend fun getNews(): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$BASE_URL/live_news.json").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                return@withContext parseNews(body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    private fun parseFaculty(json: String): FacultyData {
        val root = JSONObject(json)
        val leaders = mutableListOf<Leader>()
        val departments = mutableListOf<Department>()

        root.optJSONArray("leaders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val l = arr.getJSONObject(i)
                leaders.add(
                    Leader(
                        id = l.optString("id"),
                        fullName = l.optString("fullName"),
                        role = l.optString("role"),
                        degree = l.optString("degree"),
                        photo = l.optString("photo"),
                        phone = l.optString("phone"),
                        email = l.optString("email"),
                        reception = l.optString("reception"),
                        room = l.optString("room"),
                        bio = l.optString("bio")
                    )
                )
            }
        }

        root.optJSONArray("departments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                val programs = mutableListOf<String>()
                d.optJSONArray("programs")?.let { pa ->
                    for (j in 0 until pa.length()) programs.add(pa.getString(j))
                }
                departments.add(
                    Department(
                        id = d.optString("id"),
                        name = d.optString("name"),
                        head = d.optString("head"),
                        description = d.optString("description"),
                        programs = programs
                    )
                )
            }
        }

        return FacultyData(
            facultyName = root.optString("facultyName", "Факультет ЦЭ и ИТ"),
            universityName = root.optString("universityName", "ТГЭУ"),
            leaders = leaders,
            departments = departments
        )
    }

    private fun parseNews(json: String): List<NewsItem> {
        val result = mutableListOf<NewsItem>()
        try {
            val root = JSONObject(json)
            val arr: JSONArray = root.optJSONArray("results") ?: JSONArray(json)
            for (i in 0 until minOf(arr.length(), 30)) {
                val n = arr.getJSONObject(i)
                val rawDate = n.optString("publish_date", "")
                val displayDate = formatDate(rawDate)
                result.add(
                    NewsItem(
                        id = n.optInt("id"),
                        title = n.optString("title"),
                        thumbnailUrl = n.optString("thumbnail_url"),
                        publishDate = displayDate,
                        views = n.optInt("views"),
                        category = n.optString("category", "Новости")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun formatDate(raw: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(raw) ?: return raw
            val out = SimpleDateFormat("d MMM yyyy", Locale("ru"))
            out.format(date)
        } catch (e: Exception) {
            raw.take(10)
        }
    }

    private fun emptyFaculty() = FacultyData("", "", emptyList(), emptyList())
}
