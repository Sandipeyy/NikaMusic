// SongEditState.kt

package com.sandip.nikamusic.ui.menu.songedit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sandip.nikamusic.db.MusicDatabase
import com.sandip.nikamusic.db.entities.EditedArtist
import com.sandip.nikamusic.lyrics.LyricsHelper
import com.sandip.nikamusic.models.MediaMetadata
import com.sandip.nikamusic.models.toMediaMetadata
import com.sandip.nikamusic.playback.SongTuning
import com.sandip.nikamusic.songedit.CoverPick
import com.sandip.nikamusic.songedit.PickedLyrics
import com.sandip.nikamusic.songedit.SongEditDraft
import kotlin.math.abs

// lyric offset range from the mock: two minutes early to three minutes late
internal const val OffsetMinMs = -120_000
internal const val OffsetMaxMs = 180_000

internal const val SpeedMin = 0.25f
internal const val SpeedMax = 5f

// what the edit sheet is changing; plain state outside composition, because the popup host composes the body and the ok bar apart
@Stable
internal class SongEditState(
    val metadata: MediaMetadata,
    val currentCover: String?,
    title: String,
    artists: List<EditedArtist>,
    album: String,
    year: Int?,
    lyricsOffsetMs: Int,
    speed: Float,
    eqPresetId: String?,
) {
    private val startSpeed = speed

    var title by mutableStateOf(title)
    var cover by mutableStateOf<CoverPick?>(null)
    val artists = mutableStateListOf<EditedArtist>().apply { addAll(artists) }
    var album by mutableStateOf(album)
    var year by mutableStateOf(year?.toString().orEmpty())
    var lyricsOffsetMs by mutableIntStateOf(lyricsOffsetMs)
    var speed by mutableFloatStateOf(speed)
    var eqPresetId by mutableStateOf(eqPresetId)
    var pickedLyrics by mutableStateOf<PickedLyrics?>(null)
    // another source's lyrics picked in the dropdown, applied on ok
    var lyricsSource by mutableStateOf<LyricsHelper.ProviderCandidate?>(null)

    // set while ok is saving: the button is off and the sheet can't be pulled away
    var busy by mutableStateOf(false)

    val songId: String get() = metadata.id

    fun draft() = SongEditDraft(
        title = title,
        cover = cover,
        artists = artists.toList(),
        album = album,
        year = year.trim().toIntOrNull(),
        lyricsOffsetMs = lyricsOffsetMs,
        speed = speed.takeIf { abs(it - startSpeed) > 0.001f },
        eqPresetId = eqPresetId,
        customLyrics = pickedLyrics?.text,
    )

    companion object {
        // the song as the database has it now, edits included; null when it isn't saved at all
        suspend fun load(database: MusicDatabase, tuning: SongTuning, songId: String): SongEditState? {
            val song = database.getSongById(songId) ?: return null
            return SongEditState(
                metadata = song.toMediaMetadata(),
                currentCover = song.song.thumbnailUrl,
                title = song.song.title,
                artists = song.artists.map { EditedArtist(it.id, it.name, it.thumbnailUrl) },
                album = song.song.albumName ?: song.album?.title.orEmpty(),
                year = song.song.year,
                lyricsOffsetMs = song.song.lyricsOffset,
                speed = tuning.of(songId)?.speed ?: 1f,
                eqPresetId = tuning.eqPresetOf(songId),
            )
        }
    }
}
