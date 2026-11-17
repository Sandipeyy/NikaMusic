// DeezerLyricsProvider.kt

package com.sandip.nikamusic.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import com.sandip.nikamusic.constants.EnableDeezerKey
import com.sandip.nikamusic.utils.dataStore
import com.sandip.nikamusic.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
