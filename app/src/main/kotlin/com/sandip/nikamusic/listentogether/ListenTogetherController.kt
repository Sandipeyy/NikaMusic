// NikaMusic Listen Together (protocol v2, server in server/listen-together; the address is built in, see ListenTogetherConfig): one person hosts and everyone else follows,
// with members, a shared queue, song suggestions from guests, host controls and automatic reconnection.
package com.sandip.nikamusic.listentogether

import android.net.Uri
import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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

enum class ListenPhase { IDLE, CONNECTING, ACTIVE, RECONNECTING }
enum class ListenRole { NONE, HOST, GUEST }

data class ListenMember(val id: String, val name: String, val avatar: String?, val isHost: Boolean, val online: Boolean)
data class QueueEntry(val id: String, val title: String, val artist: String)
data class Suggestion(val fromName: String, val songId: String, val title: String, val artist: String)
data class PendingInvite(val code: String)

/** What the host is playing, as this device received it. [receivedAt] is SystemClock.elapsedRealtime() on arrival. */
data class RemotePlayback(
    val songId: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val receivedAt: Long,
)

data class ListenUiState(
    val phase: ListenPhase = ListenPhase.IDLE,
    val role: ListenRole = ListenRole.NONE,
    val code: String = "",
    val you: String = "",
    val hostId: String = "",
    val members: List<ListenMember> = emptyList(),
    val nowPlaying: RemotePlayback? = null,
    val queue: List<QueueEntry> = emptyList(),
    /** an error or notice, shown once on the page */
    val message: String? = null,
    val pendingInvite: PendingInvite? = null,
) {
    val inSession: Boolean get() = phase == ListenPhase.ACTIVE || phase == ListenPhase.RECONNECTING
}

object ListenTogetherController {
    private const val ReconnectWindowMs = 100_000L
    private const val Unavailable = "Listen together isn't available right now. Please try again later."
    private const val NoConnection = "Couldn't connect. Check your internet connection and try again."
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _ui = MutableStateFlow(ListenUiState())
    val ui: StateFlow<ListenUiState> = _ui.asStateFlow()

    // newest wins: while a guest is still loading a song, older updates are dropped
    private val _incoming = MutableSharedFlow<RemotePlayback>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incoming: SharedFlow<RemotePlayback> = _incoming.asSharedFlow()

    private val _suggestions = MutableSharedFlow<Suggestion>(extraBufferCapacity = 8)
    val suggestions: SharedFlow<Suggestion> = _suggestions.asSharedFlow()

    // every entry point (profile card, player menu, invite link) bumps this; the page is opened from one place
    private val _openRequests = MutableStateFlow(0)
    val openRequests: StateFlow<Int> = _openRequests.asStateFlow()

    private class Session(val server: String, val name: String, val avatar: String?, var role: ListenRole) {
        var code = ""
        var token = ""
        var first: () -> JSONObject = { JSONObject() }
        var openAttempts = 0
    }

    private var session: Session? = null
    private var socket: WebSocket? = null
    private var generation = 0
    private var retryJob: Job? = null
    private var attempt = 0
    private var droppedAt = 0L
    private var lastState: RemotePlayback? = null
    private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

    // ---- entry points

    fun requestOpenPage() {
        _openRequests.update { it + 1 }
    }

    fun handleInvite(uri: Uri) {
        val code = uri.getQueryParameter("code")?.trim()?.uppercase()?.takeIf { it.length == 6 } ?: return
        _ui.update { it.copy(pendingInvite = PendingInvite(code)) }
        requestOpenPage()
    }

    fun clearInvite() = _ui.update { it.copy(pendingInvite = null) }
    fun clearMessage() = _ui.update { it.copy(message = null) }
    fun notice(text: String) = _ui.update { it.copy(message = text) }

    fun inviteLink(): String {
        val s = session ?: return ""
        return "nikamusic://listen?code=${s.code}"
    }

    fun inviteText(): String {
        val s = session ?: return ""
        return "Join my NikaMusic room and listen with me!\nCode: ${s.code}\n${inviteLink()}"
    }

    // ---- session

    fun host(name: String, avatar: String?) =
        start(name, avatar, ListenRole.HOST) {
            JSONObject().put("t", "create").put("name", cleanName(name)).apply { avatar?.let { put("avatar", it) } }
        }

    fun join(name: String, avatar: String?, code: String) {
        val clean = code.trim().uppercase()
        if (clean.length != 6) {
            notice("Enter the 6-character room code")
            return
        }
        start(name, avatar, ListenRole.GUEST) {
            JSONObject().put("t", "join").put("code", clean).put("name", cleanName(name)).apply { avatar?.let { put("avatar", it) } }
        }
    }

    fun leave() {
        send(JSONObject().put("t", "leave"))
        endSession(null)
    }

    /** Host only. */
    fun kick(memberId: String) {
        if (_ui.value.role == ListenRole.HOST) send(JSONObject().put("t", "kick").put("id", memberId))
    }

    /** Host only: tell the room what is playing. */
    fun publish(songId: String, title: String, artist: String, playing: Boolean, positionMs: Long) {
        if (_ui.value.role != ListenRole.HOST || _ui.value.phase != ListenPhase.ACTIVE) return
        send(
            JSONObject().put("t", "state").put("songId", songId).put("title", title.take(200)).put("artist", artist.take(200))
                .put("playing", playing).put("pos", positionMs),
        )
    }

    /** Host only: the upcoming songs, so everyone can see them. */
    fun publishQueue(items: List<QueueEntry>) {
        if (_ui.value.role != ListenRole.HOST || _ui.value.phase != ListenPhase.ACTIVE) return
        val array = JSONArray()
        items.take(50).forEach { array.put(JSONObject().put("id", it.id).put("title", it.title.take(120)).put("artist", it.artist.take(120))) }
        send(JSONObject().put("t", "queue").put("items", array))
    }

    /** Guest only: ask the host to add a song to the shared queue. */
    fun suggest(songId: String, title: String, artist: String) {
        val ui = _ui.value
        if (ui.role != ListenRole.GUEST || ui.phase != ListenPhase.ACTIVE) {
            notice("Join a room first")
            return
        }
        send(JSONObject().put("t", "suggest").put("songId", songId).put("title", title.take(120)).put("artist", artist.take(120)))
        notice("Sent to the host: $title")
    }

    /** Guest: jump back to where the host is now. */
    fun resync() {
        val last = lastState ?: return
        val now = SystemClock.elapsedRealtime()
        val pos = last.positionMs + if (last.playing) now - last.receivedAt else 0L
        _incoming.tryEmit(last.copy(positionMs = pos, receivedAt = now))
    }

    // ---- connection

    private fun cleanName(name: String) = name.trim().ifBlank { "Guest" }.take(24)

    private fun send(json: JSONObject) {
        socket?.send(json.toString())
    }

    private fun start(name: String, avatar: String?, role: ListenRole, first: () -> JSONObject) {
        if (session != null) return
        if (!ListenTogetherConfig.available) {
            notice(Unavailable)
            return
        }
        session = Session(ListenTogetherConfig.server, name, avatar, role).also { it.first = first }
        attempt = 0
        _ui.update { it.copy(phase = ListenPhase.CONNECTING, role = role, message = null, nowPlaying = null, queue = emptyList(), members = emptyList()) }
        connect(first)
    }

    private fun connect(first: () -> JSONObject) {
        val s = session ?: return
        val gen = ++generation
        socket?.cancel()
        val request = try {
            Request.Builder().url(s.server).build()
        } catch (e: IllegalArgumentException) {
            session = null
            _ui.update { it.copy(phase = ListenPhase.IDLE, role = ListenRole.NONE, message = Unavailable) }
            return
        }
        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    scope.launch { if (gen == generation) webSocket.send(first().toString()) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { if (gen == generation) handle(text) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch { if (gen == generation) connectionLost() }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scope.launch { if (gen == generation) connectionLost() }
                }
            },
        )
    }

    /** The socket went away. Before we were in a room that is a failure; inside one we keep trying to get our seat back. */
    private fun connectionLost() {
        val s = session ?: return
        socket = null
        if (s.token.isBlank()) {
            // never got in. The first try can be slow while the service wakes up, so try again once before giving up
            if (s.openAttempts < 1) {
                s.openAttempts++
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(2_000)
                    connect(s.first)
                }
                return
            }
            session = null
            _ui.update { it.copy(phase = ListenPhase.IDLE, role = ListenRole.NONE, message = NoConnection) }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (_ui.value.phase != ListenPhase.RECONNECTING) {
            droppedAt = now
            attempt = 0
        }
        if (now - droppedAt > ReconnectWindowMs) {
            endSession("The connection was lost")
            return
        }
        _ui.update { it.copy(phase = ListenPhase.RECONNECTING) }
        val wait = min(1000L shl min(attempt, 4), 15_000L)
        attempt++
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            connect { JSONObject().put("t", "rejoin").put("code", s.code).put("token", s.token) }
        }
    }

    private fun endSession(message: String?) {
        retryJob?.cancel()
        generation++
        socket?.close(1000, "bye")
        socket = null
        session = null
        lastState = null
        _ui.update { ListenUiState(message = message, pendingInvite = it.pendingInvite) }
    }

    private fun handle(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        when (msg.optString("t")) {
            "created" -> applySnapshot(msg, ListenRole.HOST)
            "joined" -> applySnapshot(msg, ListenRole.GUEST)
            "rejoined" -> applySnapshot(msg, session?.role ?: ListenRole.GUEST)
            "members" -> _ui.update { it.copy(members = members(msg.optJSONArray("members"))) }
            "state" -> emitState(msg)
            "queue" -> _ui.update { it.copy(queue = queue(msg.optJSONArray("items"))) }
            "suggestion" -> {
                val id = msg.optString("songId")
                if (videoIdPattern.matches(id)) {
                    _suggestions.tryEmit(Suggestion(msg.optString("name").take(24), id, msg.optString("title").take(120), msg.optString("artist").take(120)))
                }
            }
            "kicked" -> endSession("The host removed you from the room")
            "closed" -> endSession(
                when (msg.optString("reason")) {
                    "host_left" -> "The host ended the room"
                    "host_gone" -> "The host left the room"
                    else -> "The room ended"
                },
            )
            "error" -> {
                val code = msg.optString("code")
                val text = msg.optString("msg").ifBlank { "Something went wrong" }
                if (code == "expired" || code == "no_room" && _ui.value.phase == ListenPhase.RECONNECTING) {
                    endSession("This room has ended")
                } else if (_ui.value.phase == ListenPhase.CONNECTING) {
                    socket?.close(1000, "error")
                    socket = null
                    session = null
                    generation++
                    _ui.update { it.copy(phase = ListenPhase.IDLE, role = ListenRole.NONE, message = text) }
                } else {
                    notice(text)
                }
            }
        }
    }

    private fun applySnapshot(msg: JSONObject, role: ListenRole) {
        val s = session ?: return
        s.token = msg.optString("token")
        s.code = msg.optString("code")
        s.role = role
        attempt = 0
        retryJob?.cancel()
        _ui.update {
            it.copy(
                phase = ListenPhase.ACTIVE,
                role = role,
                code = s.code,
                you = msg.optString("you"),
                hostId = msg.optString("hostId"),
                members = members(msg.optJSONArray("members")),
                queue = msg.optJSONArray("queue")?.let(::queue) ?: it.queue,
                message = null,
            )
        }
        msg.optJSONObject("state")?.let(::emitState)
    }

    private fun members(array: JSONArray?): List<ListenMember> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull {
            val m = array.optJSONObject(it) ?: return@mapNotNull null
            ListenMember(
                id = m.optString("id"),
                name = m.optString("name").take(24),
                avatar = m.optString("avatar").takeIf { a -> a.isNotEmpty() && a != "null" },
                isHost = m.optBoolean("host"),
                online = m.optBoolean("online", true),
            )
        }

    private fun queue(array: JSONArray?): List<QueueEntry> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull {
            val q = array.optJSONObject(it) ?: return@mapNotNull null
            val id = q.optString("id")
            if (videoIdPattern.matches(id)) QueueEntry(id, q.optString("title").take(120), q.optString("artist").take(120)) else null
        }

    private fun emitState(json: JSONObject) {
        val id = json.optString("songId")
        if (!videoIdPattern.matches(id)) return // never trust the server blindly
        val playing = json.optBoolean("playing", false)
        val age = json.optLong("age", 0L).coerceIn(0L, 60_000L)
        val state = RemotePlayback(
            songId = id,
            title = json.optString("title").take(200),
            artist = json.optString("artist").take(200),
            playing = playing,
            positionMs = json.optLong("pos", 0L).coerceAtLeast(0L) + if (playing) age else 0L,
            receivedAt = SystemClock.elapsedRealtime(),
        )
        lastState = state
        _ui.update { it.copy(nowPlaying = state) }
        _incoming.tryEmit(state)
    }
}
