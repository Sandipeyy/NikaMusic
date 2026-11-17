// ItemsPage.kt

package com.sandip.nikamusic.models

import com.music.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
