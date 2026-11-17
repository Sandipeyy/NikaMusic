// NikaMusic Listen Together: one person hosts, friends follow along. Own small protocol, see server/listen-together.
package com.sandip.nikamusic.listentogether

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

enum class ListenRole { NONE, HOST, GUEST }

data class ListenUiState(
    val dialogVisible: Boolean = false,
    val connecting: Boolean = false,
    val role: ListenRole = ListenRole.NONE,
    val code: String = "",
    val members: List<String> = emptyList(),
    val message: String? = null,
)

/** What the host is playing, as the guest received it. [receivedAt] is SystemClock.elapsedRealtime() on arrival. */
data class RemotePlayback(
    val songId: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val receivedAt: Long,
)

object ListenTogetherController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _ui = MutableStateFlow(ListenUiState())
    val ui: StateFlow<ListenUiState> = _ui.asStateFlow()

    // newest wins: while a guest is still loading a song, older updates are dropped
    private val _incoming = MutableSharedFlow<RemotePlayback>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incoming: SharedFlow<RemotePlayback> = _incoming.asSharedFlow()

    private var socket: WebSocket? = null
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun showDialog() = _ui.update { it.copy(dialogVisible = true) }
    fun hideDialog() = _ui.update { it.copy(dialogVisible = false) }
    fun clearMessage() = _ui.update { it.copy(message = null) }

    fun host(server: String, name: String) = connect(server) { send(JSONObject().put("t", "create").put("name", cleanName(name))) }

    fun join(server: String, name: String, code: String) {
        val clean = code.trim().uppercase()
        if (clean.length != 6) {
            _ui.update { it.copy(message = "Enter the 6-character room code") }
            return
        }
        connect(server) { send(JSONObject().put("t", "join").put("code", clean).put("name", cleanName(name))) }
    }

    fun leave() {
        socket?.close(1000, "bye")
        socket = null
        _ui.update { it.copy(connecting = false, role = ListenRole.NONE, code = "", members = emptyList()) }
    }

    /** Host only: tell the room what is playing. */
    fun publish(songId: String, title: String, artist: String, playing: Boolean, positionMs: Long) {
        if (_ui.value.role != ListenRole.HOST) return
        send(
            JSONObject().put("t", "state").put("songId", songId).put("title", title.take(200)).put("artist", artist.take(200))
                .put("playing", playing).put("pos", positionMs),
        )
    }

    private fun cleanName(name: String) = name.trim().ifBlank { "Guest" }.take(24)

    private fun send(json: JSONObject) {
        socket?.send(json.toString())
    }

    private fun connect(server: String, onOpened: () -> Unit) {
        if (_ui.value.connecting || _ui.value.role != ListenRole.NONE) return
        val url = server.trim()
        if (!url.startsWith("wss://")) {
            _ui.update { it.copy(message = "The server address must start with wss://") }
            return
        }
        _ui.update { it.copy(connecting = true, message = null) }
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            _ui.update { it.copy(connecting = false, message = "That server address is not valid") }
            return
        }
        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    scope.launch { onOpened() }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { handle(text) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch { dropped(webSocket, "The session ended") }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch { dropped(webSocket, "Could not reach the server") }
                }
            },
        )
    }

    private fun dropped(webSocket: WebSocket, text: String) {
        if (webSocket !== socket) return
        socket = null
        val wasActive = _ui.value.role != ListenRole.NONE || _ui.value.connecting
        _ui.update { it.copy(connecting = false, role = ListenRole.NONE, code = "", members = emptyList(), message = if (wasActive) text else it.message) }
    }

    private fun handle(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        when (msg.optString("t")) {
            "created" -> _ui.update { it.copy(connecting = false, role = ListenRole.HOST, code = msg.optString("code"), members = names(msg.optJSONArray("members"))) }
            "joined" -> {
                _ui.update { it.copy(connecting = false, role = ListenRole.GUEST, code = msg.optString("code"), members = names(msg.optJSONArray("members"))) }
                msg.optJSONObject("state")?.let(::emitState)
            }
            "members" -> _ui.update { it.copy(members = names(msg.optJSONArray("members"))) }
            "state" -> emitState(msg)
            "closed" -> {
                socket?.close(1000, "closed")
                socket = null
                _ui.update { it.copy(connecting = false, role = ListenRole.NONE, code = "", members = emptyList(), message = "The host ended the session") }
            }
            "error" -> {
                val errorText = msg.optString("msg").ifBlank { "Something went wrong" }
                if (_ui.value.role == ListenRole.NONE) {
                    socket?.close(1000, "error")
                    socket = null
                }
                _ui.update { it.copy(connecting = false, message = errorText) }
            }
        }
    }

    private fun names(array: JSONArray?): List<String> =
        if (array == null) emptyList() else (0 until array.length()).map { array.optString(it).take(24) }

    private fun emitState(json: JSONObject) {
        val id = json.optString("songId")
        if (!videoIdPattern.matches(id)) return // never trust the server blindly
        val age = json.optLong("age", 0L).coerceIn(0L, 60_000L)
        _incoming.tryEmit(
            RemotePlayback(
                songId = id,
                title = json.optString("title").take(200),
                artist = json.optString("artist").take(200),
                playing = json.optBoolean("playing", false),
                positionMs = json.optLong("pos", 0L).coerceAtLeast(0L) + if (json.optBoolean("playing", false)) age else 0L,
                receivedAt = SystemClock.elapsedRealtime(),
            ),
        )
    }
}
