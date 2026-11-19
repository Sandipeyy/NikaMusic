// UI state for the AI song insights dialog (same pattern as RingtoneController).
package com.sandip.nikamusic.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.sandip.nikamusic.constants.OpenRouterApiKey
import com.sandip.nikamusic.constants.OpenRouterModelKey
import com.sandip.nikamusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSongUiState(
    val visible: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val mode: InsightMode? = null,
    val loading: Boolean = false,
    val text: String? = null,
    val similar: List<SimilarSong> = emptyList(),
    val error: String? = null,
)

object AiSongController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AiSongUiState())
    val state: StateFlow<AiSongUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun open(title: String, artist: String) {
        job?.cancel()
        _state.value = AiSongUiState(visible = true, title = title, artist = artist)
    }

    fun close() {
        job?.cancel()
        _state.value = AiSongUiState()
    }

    fun saveKey(context: Context, key: String, model: String) {
        scope.launch {
            context.applicationContext.dataStore.edit {
                it[OpenRouterApiKey] = key.trim()
                it[OpenRouterModelKey] = model.trim().ifBlank { AiSongInsights.DEFAULT_MODEL }
            }
        }
    }

    fun run(context: Context, mode: InsightMode) {
        val s = _state.value
        job?.cancel()
        _state.update { it.copy(mode = mode, loading = true, text = null, similar = emptyList(), error = null) }
        job = scope.launch {
            AiSongInsights.ask(context.applicationContext, mode, s.title, s.artist)
                .onSuccess { answer ->
                    _state.update {
                        if (mode == InsightMode.SIMILAR) {
                            val songs = AiSongInsights.parseSimilar(answer)
                            if (songs.isEmpty()) it.copy(loading = false, error = "Could not read the AI's suggestions, try again")
                            else it.copy(loading = false, similar = songs)
                        } else it.copy(loading = false, text = answer)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Something went wrong") } }
        }
    }
}
