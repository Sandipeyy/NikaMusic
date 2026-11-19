package com.sandip.nikamusic.listentogether

import com.sandip.nikamusic.BuildConfig

/**
 * Where Listen together connects. It is set when the app is built (the LISTEN_TOGETHER_SERVER secret or local.properties),
 * and nothing in the app shows it or lets anyone change it: people just create or join a room.
 */
internal object ListenTogetherConfig {
    val server: String get() = BuildConfig.LISTEN_TOGETHER_SERVER.trim()

    /** false in a build that was given no backend; the page then says Listen together isn't available instead of showing controls that can't work */
    val available: Boolean get() = server.startsWith("wss://")
}
