// Playback sync for Listen Together. Mount once near the root, inside the player provider and the nav host.
// The page itself lives in ui/screens/listentogether; every entry point asks the controller to open it.
package com.sandip.nikamusic.ui.component

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.sandip.nikamusic.LocalPlayerConnection
import com.sandip.nikamusic.extensions.toMediaItem
import com.sandip.nikamusic.listentogether.ListenRole
import com.sandip.nikamusic.listentogether.ListenTogetherController
import com.sandip.nikamusic.listentogether.QueueEntry
import com.sandip.nikamusic.listentogether.RemotePlayback
import com.sandip.nikamusic.models.toMediaMetadata
import com.sandip.nikamusic.playback.PlayerConnection
import com.sandip.nikamusic.playback.queues.YouTubeQueue
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val ListenTogetherRoute = "listen_together"

@Composable
fun ListenTogetherHost(navController: NavController) {
    val ui by ListenTogetherController.ui.collectAsState()
    val playerConnection = LocalPlayerConnection.current

    // the profile card, the player menu and invite links all end up here: one page, one place that opens it
    LaunchedEffect(Unit) {
        var handled = 0
        ListenTogetherController.openRequests.collect { requests ->
            if (requests > handled) {
                handled = requests
                navController.navigate(ListenTogetherRoute) { launchSingleTop = true }
            }
        }
    }

    // host: tell the room what is playing when the song or play state changes, after a seek, and every few seconds;
    // and what is coming up next whenever the queue changes
    LaunchedEffect(ui.role, ui.phase, playerConnection) {
        val pc = playerConnection ?: return@LaunchedEffect
        if (ui.role != ListenRole.HOST || ui.phase != com.sandip.nikamusic.listentogether.ListenPhase.ACTIVE) return@LaunchedEffect
        var lastKey = ""
        var lastPos = 0L
        var lastPlaying = false
        var lastSent = 0L
        var lastQueue: String? = null
        var tick = 0
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
                if (tick % 2 == 0) {
                    val entries = upcoming(pc)
                    val signature = entries.joinToString("|") { it.id }
                    if (signature != lastQueue) {
                        lastQueue = signature
                        ListenTogetherController.publishQueue(entries)
                    }
                }
            }
            tick++
            delay(500)
        }
    }

    // host: songs a guest suggests go straight into the queue
    LaunchedEffect(ui.role, playerConnection) {
        val pc = playerConnection ?: return@LaunchedEffect
        if (ui.role != ListenRole.HOST) return@LaunchedEffect
        ListenTogetherController.suggestions.collect { suggestion ->
            val item = withContext(Dispatchers.IO) { YouTube.queue(listOf(suggestion.songId), null).getOrNull()?.firstOrNull() }
            if (item != null) {
                pc.addToQueue(item.toMediaItem())
                ListenTogetherController.notice("${suggestion.fromName} added “${item.title}” to the queue")
            }
        }
    }

    // guest: follow the host
    LaunchedEffect(ui.role, playerConnection) {
        val pc = playerConnection ?: return@LaunchedEffect
        if (ui.role != ListenRole.GUEST) return@LaunchedEffect
        ListenTogetherController.incoming.collect { follow(pc, it) }
    }
}

/** The song playing now and what follows it, at most 50. */
private fun upcoming(pc: PlayerConnection): List<QueueEntry> {
    val player = runCatching { pc.player }.getOrNull() ?: return emptyList()
    val count = player.mediaItemCount
    if (count == 0) return emptyList()
    val from = player.currentMediaItemIndex.coerceIn(0, count - 1)
    return (from until minOf(count, from + 50)).map { index ->
        val item = player.getMediaItemAt(index)
        QueueEntry(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty())
    }
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
