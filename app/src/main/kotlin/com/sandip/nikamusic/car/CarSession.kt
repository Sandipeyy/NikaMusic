// CarSession.kt

package com.sandip.nikamusic.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.media.MediaConstants
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.sandip.nikamusic.car.screens.CarTabsScreen
import com.sandip.nikamusic.db.MusicDatabase
import com.sandip.nikamusic.link.phone.PhoneLinkHost
import com.sandip.nikamusic.link.phone.PhonePairing
import com.sandip.nikamusic.lyrics.LyricsHelper
import kotlinx.coroutines.launch

// one run of nika on the car screen: binds the music service while it lasts and starts on the tabs
@UnstableApi
class CarSession(
    private val database: MusicDatabase,
    private val lyricsHelper: LyricsHelper,
    private val pairing: PhonePairing,
    private val link: PhoneLinkHost,
) : Session() {
    private var app: CarApp? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val context = carContext
        val playback = CarPlayback(context.applicationContext, database, lifecycleScope)
        val car = CarApp(
            carContext = context,
            scope = lifecycleScope,
            playback = playback,
            catalog = CarCatalog(context.applicationContext, database),
            images = CarImages(context.applicationContext),
            lyrics = CarLyricsSource(database, lyricsHelper),
            pairing = pairing,
            link = link,
        )
        app = car
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) = playback.bind()

                override fun onDestroy(owner: LifecycleOwner) {
                    playback.unbind()
                    app = null
                }
            },
        )
        lifecycleScope.launch { playback.service.collect { if (it != null) car.registerSession() } }
        if (wantsPlayer(intent)) car.showNowPlaying()
        return CarTabsScreen(car)
    }

    // android auto asks for the player, from its own now playing card or the mini control bar
    override fun onNewIntent(intent: Intent) {
        if (wantsPlayer(intent)) app?.showNowPlaying()
    }

    private fun wantsPlayer(intent: Intent) = intent.action == MediaConstants.ACTION_SHOW_MEDIA_PLAYBACK
}
