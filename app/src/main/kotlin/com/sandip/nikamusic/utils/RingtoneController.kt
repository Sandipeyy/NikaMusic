// Adapted from Echo Music's RingtoneViewModel (GPL-3.0, https://github.com/iad1tya/Echo-Music).
package com.sandip.nikamusic.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RingtoneUiState(
    val showTrimmer: Boolean = false,
    val showProgress: Boolean = false,
    val songId: String? = null,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val isComplete: Boolean = false,
    val isSuccess: Boolean = false,
)

/** "Set as ringtone" from any song menu: the menu calls [request], and RingtoneHost in MainActivity shows the dialogs. */
object RingtoneController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RingtoneUiState())
    val state: StateFlow<RingtoneUiState> = _state.asStateFlow()

    fun request(songId: String, title: String, artist: String, durationSeconds: Int) {
        _state.update {
            RingtoneUiState(showTrimmer = true, songId = songId, title = title, artist = artist, durationMs = durationSeconds * 1000L)
        }
    }

    fun hideTrimmer() = _state.update { it.copy(showTrimmer = false) }

    suspend fun resolveStream(context: Context, songId: String) = RingtoneHelper.resolveStream(context.applicationContext, songId)

    fun confirm(context: Context, startMs: Long, endMs: Long) {
        val s = _state.value
        val id = s.songId ?: return
        val app = context.applicationContext
        _state.update {
            it.copy(showTrimmer = false, showProgress = true, progress = 0f, statusMessage = "Starting...", isComplete = false, isSuccess = false)
        }
        scope.launch {
            RingtoneHelper.downloadAndTrimAsRingtone(
                context = app, songId = id, title = s.title, artist = s.artist, startMs = startMs, endMs = endMs,
                onProgress = { p, m -> _state.update { it.copy(progress = p, statusMessage = m) } },
                onComplete = { ok, m -> _state.update { it.copy(isComplete = true, isSuccess = ok, statusMessage = m, progress = if (ok) 1f else it.progress) } },
            )
        }
    }

    fun dismissProgress() = _state.update { it.copy(showProgress = false, isComplete = false) }

    fun openSoundSettings(context: Context) {
        RingtoneHelper.openSoundSettings(context)
        dismissProgress()
    }
}
