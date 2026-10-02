package uz.riat.tdiu.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = mutableMapOf<String, Bitmap>()

    fun load(imageView: ImageView, url: String) {
        if (url.isEmpty()) return
        cache[url]?.let {
            imageView.setImageBitmap(it)
            return
        }
        imageView.tag = url
        executor.execute {
            val bitmap = fetchBitmap(url)
            mainHandler.post {
                if (imageView.tag == url && bitmap != null) {
                    cache[url] = bitmap
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun fetchBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            null
        }
    }
}
