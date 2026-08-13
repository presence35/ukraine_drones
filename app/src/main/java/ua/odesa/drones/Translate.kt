package ua.odesa.drones

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Free, unofficial Google Translate endpoint (client=gtx). Used only to present
 * NEPTUN course text in English; raw Ukrainian text is the fallback.
 */
object Translator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun translate(ukText: String): String? = withContext(Dispatchers.IO) {
        if (ukText.isBlank()) return@withContext null
        cache[ukText]?.let { return@withContext it }
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=uk&tl=en&dt=t&q=" +
            java.net.URLEncoder.encode(ukText, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val segs = JSONArray(body).optJSONArray(0) ?: return@withContext null
                val sb = StringBuilder()
                for (i in 0 until segs.length()) {
                    val hit = segs.optJSONArray(i) ?: continue
                    val text = hit.optString(0)
                    if (text.isNotEmpty()) sb.append(text)
                }
                val out = sb.toString().trim()
                if (out.isEmpty()) null else {
                    if (cache.size >= 500) cache.clear()
                    cache[ukText] = out
                    out
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}