// Adapted from Echo Music's RingtoneTrimmerDialog / RingtoneProgressDialog (GPL-3.0, https://github.com/iad1tya/Echo-Music).
package com.sandip.nikamusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.sandip.nikamusic.R
import com.sandip.nikamusic.utils.RingtoneController
import com.sandip.nikamusic.utils.makeTimeString
import kotlinx.coroutines.delay

/** Mount once near the root of the app; shows the trimmer and the progress dialogs for [RingtoneController]. */
@Composable
fun RingtoneHost() {
    val state by RingtoneController.state.collectAsState()
    val context = LocalContext.current

    if (state.showTrimmer && state.songId != null) {
        RingtoneTrimmerDialog(
            songId = state.songId!!,
            songTitle = state.title,
            durationMs = state.durationMs,
            onDismiss = { RingtoneController.hideTrimmer() },
            onConfirm = { start, end -> RingtoneController.confirm(context, start, end) },
        )
    }
    if (state.showProgress) {
        RingtoneProgressDialog(
            progress = state.progress,
            message = state.statusMessage,
            isComplete = state.isComplete,
            isSuccess = state.isSuccess,
            onDismiss = { RingtoneController.dismissProgress() },
            onOpenSettings = { RingtoneController.openSoundSettings(context) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RingtoneTrimmerDialog(
    songId: String,
    songTitle: String,
    durationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    val maxSec = 30f
    val totalSec = if (durationMs > 0) durationMs / 1000f else 180f
    var range by remember(songId) { mutableStateOf(0f..minOf(maxSec, totalSec)) }

    LaunchedEffect(songId) {
        isLoading = true
        val stream = RingtoneController.resolveStream(context, songId)
        if (stream == null) {
            failed = true
        } else {
            val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(stream.headers)
            player.setMediaSource(ProgressiveMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(stream.url)))
            player.prepare()
        }
        isLoading = false
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (player.currentPosition >= (range.endInclusive * 1000).toLong()) {
                player.pause()
                isPlaying = false
            }
            delay(100)
        }
    }

    AlertDialog(
        onDismissRequest = { player.stop(); onDismiss() },
        title = { Text(stringResource(R.string.ringtone_trim_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ringtone_trim_hint, songTitle), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    when {
                        isLoading -> CircularProgressIndicator(Modifier.size(48.dp))
                        failed -> Text(stringResource(R.string.ringtone_stream_failed), color = MaterialTheme.colorScheme.error)
                        else -> IconButton(onClick = {
                            if (isPlaying) {
                                player.pause(); isPlaying = false
                            } else {
                                player.seekTo((range.start * 1000).toLong()); player.play(); isPlaying = true
                            }
                        }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(48.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(makeTimeString((range.start * 1000).toLong()), color = MaterialTheme.colorScheme.primary)
                    Text(makeTimeString((range.endInclusive * 1000).toLong()), color = MaterialTheme.colorScheme.primary)
                }
                RangeSlider(
                    value = range,
                    onValueChange = { range = it; if (isPlaying) { player.pause(); isPlaying = false } },
                    valueRange = 0f..totalSec,
                    modifier = Modifier.fillMaxWidth(),
                )
                val selected = (range.endInclusive - range.start).toLong()
                Text(
                    stringResource(R.string.ringtone_selected_duration, makeTimeString(selected * 1000)),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = if (selected > 40) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && range.endInclusive > range.start,
                onClick = { player.stop(); onConfirm((range.start * 1000).toLong(), (range.endInclusive * 1000).toLong()) },
            ) { Text(stringResource(R.string.ringtone_save)) }
        },
        dismissButton = { TextButton(onClick = { player.stop(); onDismiss() }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RingtoneProgressDialog(
    progress: Float,
    message: String,
    isComplete: Boolean,
    isSuccess: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (isComplete) onDismiss() },
        title = {
            Text(
                when {
                    !isComplete -> stringResource(R.string.ringtone_progress_title)
                    isSuccess -> stringResource(R.string.ringtone_done)
                    else -> stringResource(R.string.ringtone_failed)
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (!isComplete) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (isComplete) {
                TextButton(onClick = if (isSuccess) onOpenSettings else onDismiss) {
                    Text(if (isSuccess) stringResource(R.string.ringtone_open_settings) else stringResource(R.string.close))
                }
            }
        },
        dismissButton = { if (isComplete && isSuccess) TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
