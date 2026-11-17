// SecretPreferences.kt

package com.sandip.nikamusic.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sandip.nikamusic.constants.DataSyncIdKey
import com.sandip.nikamusic.constants.DeeplApiKey
import com.sandip.nikamusic.constants.DiscordAccessTokenKey
import com.sandip.nikamusic.constants.DiscordRefreshTokenKey
import com.sandip.nikamusic.constants.InnerTubeCookieKey
import com.sandip.nikamusic.constants.LastFMSessionKey
import com.sandip.nikamusic.constants.LibreFmSessionKey
import com.sandip.nikamusic.constants.MonochromePlaybackApiTokenKey
import com.sandip.nikamusic.constants.OpenRouterApiKey
import com.sandip.nikamusic.constants.ProxyPasswordKey
import com.sandip.nikamusic.constants.TurnstileBypassTokenKey
import com.sandip.nikamusic.constants.VisitorDataKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// the settings store with its secrets sealed on disk and plain in memory, so every reader and writer in the app sees them as before
class SecretPreferences(private val raw: DataStore<Preferences>) : DataStore<Preferences> {
    override val data: Flow<Preferences> = raw.data.map(::unseal)

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val stored = raw.updateData { stored -> seal(transform(unseal(stored)), stored) }
        return unseal(stored)
    }

    // secrets written before the vault existed are still plain on disk; one pass at start-up seals them
    suspend fun sealLeftovers() {
        val stored = raw.data.first()
        if (SecretKeys.none { key -> stored[key]?.let { !SecretVault.isSealed(it) } == true }) return
        raw.updateData { seal(unseal(it), it) }
    }

    companion object {
        // the account cookie, the tokens and the api keys: everything that would let someone else be this user
        val SecretKeys: List<Preferences.Key<String>> = listOf(
            InnerTubeCookieKey,
            VisitorDataKey,
            DataSyncIdKey,
            DiscordAccessTokenKey,
            DiscordRefreshTokenKey,
            LastFMSessionKey,
            LibreFmSessionKey,
            OpenRouterApiKey,
            DeeplApiKey,
            MonochromePlaybackApiTokenKey,
            TurnstileBypassTokenKey,
            ProxyPasswordKey,
        )

        // the stored preferences with every secret opened; one sealed by a key this phone doesn't have is dropped, so the user signs in again
        fun unseal(stored: Preferences): Preferences {
            val out = stored.toMutablePreferences()
            for (key in SecretKeys) {
                val value = stored[key] ?: continue
                val plain = SecretVault.open(value)
                if (plain == null) out.remove(key) else if (plain != value) out[key] = plain
            }
            return out
        }

        // what goes to disk: a secret that didn't change keeps the sealed form it had, a new or changed one is sealed afresh
        fun seal(plain: Preferences, stored: Preferences): Preferences {
            val out = plain.toMutablePreferences()
            for (key in SecretKeys) {
                val value = plain[key] ?: continue
                val before = stored[key]
                out[key] = if (before != null && SecretVault.isSealed(before) && SecretVault.open(before) == value) before else SecretVault.seal(value)
            }
            return out
        }
    }
}
