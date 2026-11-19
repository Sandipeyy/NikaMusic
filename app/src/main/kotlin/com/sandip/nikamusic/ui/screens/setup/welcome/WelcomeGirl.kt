// NikaMusic's logo on the welcome page, in the frame the layout leaves for the art. It breathes very slightly,
// driven by the page's shared clock and read at draw time so it never recomposes.

package com.sandip.nikamusic.ui.screens.setup.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.sandip.nikamusic.R
import kotlin.math.sin

@Composable
internal fun WelcomeGirl(
    motion: GirlMotion,
    seconds: FloatState,
    modifier: Modifier = Modifier,
    screen: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(0.62f)
                .graphicsLayer {
                    val breathe = 1f + 0.025f * sin(seconds.floatValue * 1.6f)
                    scaleX = breathe
                    scaleY = breathe
                },
        )
    }
}
