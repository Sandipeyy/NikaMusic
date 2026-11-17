// Dialog and playback sync for Listen Together. Mount once near the root, inside the player provider.
package com.sandip.nikamusic.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.sandip.nikamusic.LocalPlayerConnection
import com.sandip.nikamusic.constants.ListenTogetherNameKey
import com.sandip.nikamusic.constants.ListenTogetherServerKey
import com.sandip.nikamusic.listentogether.ListenRole
import com.sandip.nikamusic.listentogether.ListenTogetherController
import com.sandip.nikamusic.listentogether.RemotePlayback
import com.sandip.nikamusic.models.toMediaMetadata
import com.sandip.nikamusic.playback.PlayerConnection
import com.sandip.nikamusic.playback.queues.YouTubeQueue
import com.sandip.nikamusic.utils.rememberPreference
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ListenTogetherHost() {
    val ui by ListenTogetherController.ui.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    // host: tell the room what is playing when the song or play state changes, after a seek, and every few seconds
    LaunchedEffect(ui.role, playerConnection) {
        val pc = playerConnection ?: return@LaunchedEffect
        if (ui.role != ListenRole.HOST) return@LaunchedEffect
        var lastKey = ""
        var lastPos = 0L
        var lastPlaying = false
        var lastSent = 0L
        while (isActive) {
            val meta = pc.mediaMetadata.value
            val player = runCatching { pc.player }.getOrNull()
            if (meta != null && player != null) {
                val playing = player.playWhenReady
                val pos = player.currentPosition
                val now = SystemClock.elapsedRealtime()
                val expected = if (lastPlaying) lastPos + (now - lastSent) else lastPos
                val key = "${meta.id}|$playing"
                if (key != lastKey || abs(pos - expected) > 2500 || now - lastSent > 5000) {
                    ListenTogetherController.publish(meta.id, meta.title, meta.artists.joinToString { it.name }, playing, pos)
                    lastKey = key
                    lastPos = pos
                    lastPlaying = playing
                    lastSent = now
                }
            }
            delay(500)
        }
    }

    // guest: follow the host
    LaunchedEffect(ui.role, playerConnection) {
        val pc = playerConnection ?: return@LaunchedEffect
        if (ui.role != ListenRole.GUEST) return@LaunchedEffect
        ListenTogetherController.incoming.collect { follow(pc, it) }
    }

    if (ui.dialogVisible) ListenTogetherDialog()
}

private suspend fun follow(pc: PlayerConnection, state: RemotePlayback) {
    val player = runCatching { pc.player }.getOrNull() ?: return
    if (player.currentMediaItem?.mediaId != state.songId) {
        val item = withContext(Dispatchers.IO) { YouTube.queue(listOf(state.songId), null).getOrNull()?.firstOrNull() } ?: return
        pc.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
        withTimeoutOrNull(12_000) {
            while (player.currentMediaItem?.mediaId != state.songId || player.playbackState != androidx.media3.common.Player.STATE_READY) delay(200)
        }
    }
    val target = state.positionMs + if (state.playing) SystemClock.elapsedRealtime() - state.receivedAt else 0L
    if (abs(player.currentPosition - target) > 2000) player.seekTo(target.coerceAtLeast(0L))
    if (player.playWhenReady != state.playing) player.playWhenReady = state.playing
}

@Composable
private fun ListenTogetherDialog() {
    val context = LocalContext.current
    val ui by ListenTogetherController.ui.collectAsState()
    val (savedServer, setServer) = rememberPreference(ListenTogetherServerKey, defaultValue = "")
    val (savedName, setName) = rememberPreference(ListenTogetherNameKey, defaultValue = "")
    var server by remember(savedServer) { mutableStateOf(savedServer) }
    var name by remember(savedName) { mutableStateOf(savedName) }
    var code by remember { mutableStateOf("") }
    val connected = ui.role != ListenRole.NONE

    AlertDialog(
        onDismissRequest = { ListenTogetherController.hideDialog() },
        title = { Text("Listen together", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (connected) {
                    Text(if (ui.role == ListenRole.HOST) "You are the host. Everyone follows what you play." else "You are following the host.")
                    Spacer(Modifier.height(12.dp))
                    Text("Room code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ui.code, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    TextButton(onClick = { copy(context, ui.code) }) { Text("Copy code") }
                    Spacer(Modifier.height(8.dp))
                    Text("In the room (${ui.members.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ui.members.joinToString(", "))
                } else {
                    Text(
                        "Host a room and share the code, or join a friend's room. The host picks the music and everyone else hears it in sync.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = server, onValueChange = { server = it }, singleLine = true, enabled = !ui.connecting,
                        label = { Text("Server address (wss://…)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true, enabled = !ui.connecting,
                        label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !ui.connecting && server.isNotBlank(),
                        onClick = { setServer(server.trim()); setName(name.trim()); ListenTogetherController.host(server, name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Create a room") }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = code, onValueChange = { code = it.uppercase().take(6) }, singleLine = true, enabled = !ui.connecting,
                            label = { Text("Room code") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        )
                        OutlinedButton(
                            enabled = !ui.connecting && server.isNotBlank() && code.length == 6,
                            onClick = { setServer(server.trim()); setName(name.trim()); ListenTogetherController.join(server, name, code) },
                        ) { Text("Join") }
                    }
                    if (ui.connecting) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                ui.message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (connected) Button(onClick = { ListenTogetherController.leave() }) { Text(if (ui.role == ListenRole.HOST) "End session" else "Leave") }
            else TextButton(onClick = { ListenTogetherController.hideDialog() }) { Text("Close") }
        },
        dismissButton = {
            if (connected) TextButton(onClick = { ListenTogetherController.hideDialog() }) { Text("Hide") }
        },
    )
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Room code", text))
}
