// the "support NikaMusic's development" sheet: the auto prompt asks people to star the repo

package com.sandip.nikamusic.ui.screens.donate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandip.nikamusic.core.updater.GithubRepoUrl
import com.sandip.nikamusic.ui.component.PopupSheetHandleHeight
import com.sandip.nikamusic.ui.component.PopupSheetState
import com.sandip.nikamusic.ui.screens.update.openUrl

// how tall the blue name band is below the handle row
private val BandBody = 150.dp

/** shows the support sheet */
fun PopupSheetState.showSupportSheet() {
    val popup = this
    show(fullBleed = true, buttonBar = { SupportButtons(popup) }) {
        SupportSheetContent()
    }
}

@Composable
private fun SupportSheetContent() {
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bandHeight = PopupSheetHandleHeight + CharacterTop + BandBody

        NameMarquee(
            modifier = Modifier
                // starts up behind the handle so the blue runs right to the sheet's top edge, and takes no room of its own
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, bandHeight.roundToPx()))
                    layout(constraints.maxWidth, 0) { placeable.place(0, -PopupSheetHandleHeight.roundToPx()) }
                }
                // rides along when a short screen has to scroll, read at draw time so scrolling never recomposes
                .graphicsLayer { translationY = -scroll.value.toFloat() },
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp),
        ) {
            // the band's room, where the logo used to be
            Spacer(Modifier.height(CharacterTop + BandBody + 16.dp))
            Text(
                text = "Enjoying NikaMusic?",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Support NikaMusic’s development!",
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
                color = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "thanks for trying it out! btw, NikaMusic will always be free, forever yours! <3\n\n" +
                    "giving a star to NikaMusic’s repo is the best way to support it, and it takes one tap. thanks again!",
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = DonateSoftText,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SupportButtons(popup: PopupSheetState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DonatePillButton(
            text = "Star the repo!",
            onClick = { context.openUrl(GithubRepoUrl) },
        )
        DonatePillButton(
            text = "Maybe later",
            onClick = popup::dismiss,
            quiet = true,
        )
    }
}
