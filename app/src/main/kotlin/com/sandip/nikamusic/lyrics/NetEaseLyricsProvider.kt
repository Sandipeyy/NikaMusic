// NetEaseLyricsProvider.kt

package com.sandip.nikamusic.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.sandip.nikamusic.constants.EnableNetEaseKey
import com.sandip.nikamusic.utils.dataStore
import com.sandip.nikamusic.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
