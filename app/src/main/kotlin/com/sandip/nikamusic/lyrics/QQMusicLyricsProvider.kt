// QQMusicLyricsProvider.kt

package com.sandip.nikamusic.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.sandip.nikamusic.constants.EnableQQMusicKey
import com.sandip.nikamusic.utils.dataStore
import com.sandip.nikamusic.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
