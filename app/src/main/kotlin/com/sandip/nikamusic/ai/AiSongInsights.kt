// Optional AI helpers for the song in front of you. Nothing is sent unless you open this and tap a button,
// and only the title and artist are sent (never your library, never the lyrics), to the endpoint you set.
package com.sandip.nikamusic.ai

import android.content.Context
import com.sandip.nikamusic.constants.OpenRouterApiKey
import com.sandip.nikamusic.constants.OpenRouterBaseUrlKey
import com.sandip.nikamusic.constants.OpenRouterModelKey
import com.sandip.nikamusic.utils.dataStore
import com.sandip.nikamusic.utils.get
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class InsightMode { MEANING, REFERENCES, SIMILAR }

data class SimilarSong(val title: String, val artist: String, val why: String)

object AiSongInsights {
    const val DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    const val DEFAULT_MODEL = "openrouter/free"

    /** Only public https endpoints, so a typo or a hostile setting can't point the app at the local network. */
    fun isUrlSafe(url: String): Boolean = runCatching {
        val u = URL(url)
        val h = u.host.lowercase()
        u.protocol == "https" && h.isNotEmpty() && h != "localhost" && !h.endsWith(".local") &&
            !Regex("""^(127\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|0\.|\[)""").containsMatchIn(h)
    }.getOrDefault(false)

    suspend fun hasKey(context: Context): Boolean = context.dataStore.get(OpenRouterApiKey, "").isNotBlank()

    private fun prompt(mode: InsightMode, title: String, artist: String): Pair<String, String> {
        val song = "\"$title\" by $artist"
        return when (mode) {
            InsightMode.MEANING ->
                "You explain songs to curious listeners. Say what the song is about: its story, themes and mood, and the context it came from (album, era, what the artist has said) only where you are confident. If you don't know this song, say so plainly instead of guessing. Never quote more than a few words of the lyrics. Plain text, no markdown, under 180 words." to
                    "Explain the meaning of $song."
            InsightMode.REFERENCES ->
                "You explain the references inside songs. List the cultural, historical, literary or musical references this song is known to contain, one per line as 'Reference: short explanation'. Only include ones you are confident about, and if you know of none, say so plainly. Never quote more than a few words of the lyrics. Plain text, no markdown, at most 8 lines." to
                    "What does $song reference?"
            InsightMode.SIMILAR ->
                "You recommend music. Reply with ONLY a raw JSON object, no markdown: {\"songs\":[{\"title\":\"\",\"artist\":\"\",\"why\":\"one short sentence\"}]} with exactly 8 real songs that sound or feel like the given one. Never include the given song itself." to
                    "Recommend songs similar to $song."
        }
    }

    suspend fun ask(context: Context, mode: InsightMode, title: String, artist: String): Result<String> = withContext(Dispatchers.IO) {
        val key = context.dataStore.get(OpenRouterApiKey, "")
        val endpoint = context.dataStore.get(OpenRouterBaseUrlKey, DEFAULT_ENDPOINT).ifBlank { DEFAULT_ENDPOINT }
        val model = context.dataStore.get(OpenRouterModelKey, DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
        if (!isUrlSafe(endpoint)) return@withContext Result.failure(Exception("The AI address must be a public https address"))
        val (system, user) = prompt(mode, title.take(200), artist.take(200))
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.4)
            .put("max_tokens", 700)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", user)))
            .toString()
        try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 90_000
                doOutput = true
                if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer ${key.trim()}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("HTTP-Referer", "https://github.com/Sandipeyy/NikaMusic")
                setRequestProperty("X-Title", "NikaMusic")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val ok = conn.responseCode in 200..299
                val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (!ok) {
                    val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                    return@withContext Result.failure(Exception(message?.takeIf { it.isNotBlank() } ?: "The AI request failed (HTTP ${conn.responseCode})"))
                }
                val answer = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                if (answer.isNullOrBlank()) Result.failure(Exception("The AI returned nothing")) else Result.success(answer)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not reach the AI: ${e.message}"))
        }
    }

    fun parseSimilar(raw: String): List<SimilarSong> = runCatching {
        val clean = raw.replace("```json", "").replace("```", "").trim()
        val songs = JSONObject(clean.substring(clean.indexOf('{'), clean.lastIndexOf('}') + 1)).optJSONArray("songs") ?: return emptyList()
        (0 until songs.length()).mapNotNull {
            val s = songs.optJSONObject(it) ?: return@mapNotNull null
            val title = s.optString("title").take(120)
            if (title.isBlank()) null else SimilarSong(title, s.optString("artist").take(120), s.optString("why").take(200))
        }
    }.getOrDefault(emptyList())
}
