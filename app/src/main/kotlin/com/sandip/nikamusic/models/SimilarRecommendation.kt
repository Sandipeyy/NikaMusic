// SimilarRecommendation.kt

package com.sandip.nikamusic.models

import com.music.innertube.models.YTItem
import com.sandip.nikamusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
