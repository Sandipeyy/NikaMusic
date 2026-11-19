// The welcome page's art frame. The page lays itself out around this shape (a tall portrait box) and ticks `update`
// every frame; the art that fills it is NikaMusic's own logo (see WelcomeGirl.kt).

package com.sandip.nikamusic.ui.screens.setup.welcome

internal object GirlArt {
    // the shape of the art frame, so the layouts keep working
    const val FrameWidth = 1149f
    const val FrameHeight = 1858f

    // how far across the frame the art reaches, so layouts can keep it on screen
    const val FingertipsAcross = 0.965f
}

/** The page's clock calls this once a frame; the logo animates from the shared seconds instead, so there is nothing to step. */
internal class GirlMotion {
    fun update(seconds: Float) = Unit
}
