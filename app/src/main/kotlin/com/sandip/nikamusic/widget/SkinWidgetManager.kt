// Widget skins ported from Echo Music (GPL-3.0, https://github.com/iad1tya/Echo-Music) and adapted for NikaMusic.
// Skins: Modern Flow, Compact Glow, Showcase Card, Slim Strip, Vinyl Square, Vintage Turntable.
package com.sandip.nikamusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.sandip.nikamusic.MainActivity
import com.sandip.nikamusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkinWidgetManager private constructor(appContext: Context) {
  private val context: Context = appContext
  private val imageLoader
    get() = context.imageLoader

  private var cachedArtworkUri: String? = null
  private var cachedAlbumArt: Bitmap? = null
  private var cachedRoundedAlbumArt: Bitmap? = null
  private var cachedBannerAlbumArt: Bitmap? = null
  private var cachedAccentColor: Int = 0xFFA855F7.toInt()

  private inline fun <reified T> ids(manager: AppWidgetManager): IntArray =
    manager.getAppWidgetIds(ComponentName(context, T::class.java))

  /** Redraws every placed NikaMusic skin widget. Cheap when none are placed. */
  suspend fun update(
    title: String,
    artist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    duration: Long = 0,
    currentPosition: Long = 0,
  ) {
    val manager = AppWidgetManager.getInstance(context)
    val modern = ids<NikaModernWidgetReceiver>(manager)
    val square = ids<NikaSquareWidgetReceiver>(manager)
    val showcase = ids<NikaShowcaseWidgetReceiver>(manager)
    val slim = ids<NikaSlimWidgetReceiver>(manager)
    val vinyl = ids<NikaVinylWidgetReceiver>(manager)
    val vintage = ids<NikaVintageWidgetReceiver>(manager)
    if (listOf(modern, square, showcase, slim, vinyl, vintage).all { it.isEmpty() }) return

    if (artworkUri == null || artworkUri != cachedArtworkUri || cachedAlbumArt == null) {
      val art = artworkUri?.let { loadAlbumArt(it, 400) }
      cachedAlbumArt = art
      cachedRoundedAlbumArt = art?.let { getRoundedCornerBitmap(it, 36f) }
      cachedBannerAlbumArt = art?.let { getRoundedCornerBitmap(it, 44f) }
      cachedAccentColor = extractAccentColor(art)
      cachedArtworkUri = artworkUri
    }
    val rounded = cachedRoundedAlbumArt
    val banner = cachedBannerAlbumArt
    val accent = cachedAccentColor

    if (modern.isNotEmpty()) {
      val views = createModernFlowRemoteViews(title, artist, rounded, isPlaying, isLiked, duration, currentPosition, accent)
      modern.forEach { manager.updateAppWidget(it, views) }
    }
    if (square.isNotEmpty()) {
      val views = createCompactGlowRemoteViews(title, artist, rounded, isPlaying, accent)
      square.forEach { manager.updateAppWidget(it, views) }
    }
    if (showcase.isNotEmpty()) {
      val views = createShowcaseCardRemoteViews(title, artist, banner, isPlaying, isLiked, duration, currentPosition, accent)
      showcase.forEach { manager.updateAppWidget(it, views) }
    }
    if (slim.isNotEmpty()) {
      val views = createSlimStripRemoteViews(title, artist, rounded, isPlaying, duration, currentPosition, accent)
      slim.forEach { manager.updateAppWidget(it, views) }
    }

    // 33 1/3 RPM: one full turn every 1.8 s
    val vinylAngle = if (isPlaying && duration > 0) ((currentPosition % 1800L).toFloat() / 1800f * 360f) else 0f
    if (vinyl.isNotEmpty()) {
      val disc = createVinylDiscBitmap(cachedAlbumArt, vinylAngle, 320, false)
      val views = createVinylSquareRemoteViews(title, artist, disc, isPlaying, duration, currentPosition, accent, vinylAngle)
      vinyl.forEach { manager.updateAppWidget(it, views) }
    }
    if (vintage.isNotEmpty()) {
      val disc = createVinylDiscBitmap(cachedAlbumArt, vinylAngle, 260, true)
      val views = createVintageTurntableRemoteViews(title, artist, disc, isPlaying, duration, currentPosition, vinylAngle)
      vintage.forEach { manager.updateAppWidget(it, views) }
    }
  }

  private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
    // Ensure the bitmap is square for thumbnails
    val size = minOf(bitmap.width, bitmap.height)
    val xOffset = (bitmap.width - size) / 2
    val yOffset = (bitmap.height - size) / 2
    val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint =
      Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      }
    val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

    if (squareBitmap != bitmap) {
      squareBitmap.recycle()
    }

    return output
  }

  private fun getRoundedDefaultIcon(cornerRadius: Float): Bitmap {
    // Get the launcher icon and make it rounded
    val drawable = context.packageManager.getApplicationIcon(context.packageName)
    val size = 300
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return getRoundedCornerBitmap(bitmap, cornerRadius)
  }

  private fun getPlayPauseIntent(): PendingIntent {
    val intent =
      Intent(context, MusicWidgetReceiver::class.java).apply {
        action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
      }
    return PendingIntent.getBroadcast(
      context,
      1,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun getLikeIntent(): PendingIntent {
    val intent =
      Intent(context, MusicWidgetReceiver::class.java).apply {
        action = MusicWidgetReceiver.ACTION_LIKE
      }
    return PendingIntent.getBroadcast(
      context,
      2,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun getPreviousIntent(): PendingIntent {
    val intent =
      Intent(context, MusicWidgetReceiver::class.java).apply {
        action = MusicWidgetReceiver.ACTION_PREVIOUS
      }
    return PendingIntent.getBroadcast(
      context,
      10,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun getNextIntent(): PendingIntent {
    val intent =
      Intent(context, MusicWidgetReceiver::class.java).apply {
        action = MusicWidgetReceiver.ACTION_NEXT
      }
    return PendingIntent.getBroadcast(
      context,
      11,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  // Echo opens a sheet inside its own app here; NikaMusic just opens the app.
  private fun getNowPlayingIntent(): PendingIntent = getOpenAppIntent()

  // Echo opens a sheet inside its own app here; NikaMusic just opens the app.
  private fun getQueueIntent(): PendingIntent = getOpenAppIntent()

  // Echo opens a sheet inside its own app here; NikaMusic just opens the app.
  private fun getSongOptionsIntent(): PendingIntent = getOpenAppIntent()

  // Echo opens a sheet inside its own app here; NikaMusic just opens the app.
  private fun getOutputSwitcherIntent(): PendingIntent = getOpenAppIntent()

  private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
  }

  private fun createModernFlowRemoteViews(
    title: String,
    artist: String,
    roundedAlbumArt: Bitmap?,
    isPlaying: Boolean,
    isLiked: Boolean,
    duration: Long,
    currentPosition: Long,
    accentColor: Int
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_modern_flow)

    views.setTextViewText(R.id.widget_song_title, title)
    views.setTextViewText(R.id.widget_artist_name, artist)

    views.setImageViewBitmap(
      R.id.widget_album_art,
      roundedAlbumArt ?: getRoundedDefaultIcon(36f)
    )

    val playIcon = if (isPlaying) R.drawable.ic_widget_pause_bold_white else R.drawable.ic_widget_play_bold_white
    views.setImageViewResource(R.id.widget_play_pause, playIcon)

    val likeIcon = if (isLiked) R.drawable.ic_widget_heart_filled_clean else R.drawable.ic_widget_heart_clean
    views.setImageViewResource(R.id.widget_like_icon, likeIcon)

    if (duration > 0) {
      val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
      views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
      views.setTextViewText(R.id.widget_time_current, formatDuration(currentPosition))
      views.setTextViewText(R.id.widget_time_duration, formatDuration(duration))
    } else {
      views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
      views.setTextViewText(R.id.widget_time_current, "0:00")
      views.setTextViewText(R.id.widget_time_duration, "0:00")
    }

    views.setOnClickPendingIntent(R.id.widget_album_art, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_song_title, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_artist_name, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_cast_button, getOutputSwitcherIntent())
    views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())
    views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
    views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
    views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())
    views.setOnClickPendingIntent(R.id.widget_queue_button, getQueueIntent())

    return views
  }

  private fun createCompactGlowRemoteViews(
    title: String,
    artist: String,
    roundedAlbumArt: Bitmap?,
    isPlaying: Boolean,
    accentColor: Int
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_compact_glow)

    views.setTextViewText(R.id.widget_song_title, title)
    views.setTextViewText(R.id.widget_artist_name, artist)

    views.setImageViewBitmap(
      R.id.widget_album_art,
      roundedAlbumArt ?: getRoundedDefaultIcon(36f)
    )

    val playIcon = if (isPlaying) R.drawable.ic_widget_pause_bold_white else R.drawable.ic_widget_play_bold_white
    views.setImageViewResource(R.id.widget_play_pause, playIcon)

    views.setOnClickPendingIntent(R.id.widget_album_art, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_song_title, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_artist_name, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
    views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
    views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())

    return views
  }

  private fun createShowcaseCardRemoteViews(
    title: String,
    artist: String,
    bannerAlbumArt: Bitmap?,
    isPlaying: Boolean,
    isLiked: Boolean,
    duration: Long,
    currentPosition: Long,
    accentColor: Int
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_showcase_card)

    views.setTextViewText(R.id.widget_song_title, title)
    views.setTextViewText(R.id.widget_artist_name, artist)

    views.setImageViewBitmap(
      R.id.widget_album_art,
      bannerAlbumArt ?: getRoundedDefaultIcon(44f)
    )


    val playIcon = if (isPlaying) R.drawable.ic_widget_pause_bold_white else R.drawable.ic_widget_play_bold_white
    views.setImageViewResource(R.id.widget_play_pause, playIcon)

    val likeIcon = if (isLiked) R.drawable.ic_widget_heart_filled_clean else R.drawable.ic_widget_heart_clean
    views.setImageViewResource(R.id.widget_like_icon, likeIcon)
    views.setImageViewResource(R.id.widget_soundwave_logo, R.drawable.icon)

    if (duration > 0) {
      val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
      views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
      views.setTextViewText(R.id.widget_time_current, formatDuration(currentPosition))
      views.setTextViewText(R.id.widget_time_duration, formatDuration(duration))
    } else {
      views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
      views.setTextViewText(R.id.widget_time_current, "0:00")
      views.setTextViewText(R.id.widget_time_duration, "0:00")
    }

    views.setOnClickPendingIntent(R.id.widget_soundwave_logo, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_album_art, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_song_title, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_artist_name, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_cast_button, getOutputSwitcherIntent())
    views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())
    views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
    views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
    views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())
    views.setOnClickPendingIntent(R.id.widget_options_button, getSongOptionsIntent())

    return views
  }

  private fun createSlimStripRemoteViews(
    title: String,
    artist: String,
    roundedAlbumArt: Bitmap?,
    isPlaying: Boolean,
    duration: Long,
    currentPosition: Long,
    accentColor: Int
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_slim_strip)

    views.setTextViewText(R.id.widget_song_title, title)
    views.setTextViewText(R.id.widget_artist_name, artist)

    views.setImageViewBitmap(
      R.id.widget_album_art,
      roundedAlbumArt ?: getRoundedDefaultIcon(32f)
    )

    val playIcon = if (isPlaying) R.drawable.ic_widget_pause_bold_white else R.drawable.ic_widget_play_bold_white
    views.setImageViewResource(R.id.widget_play_pause, playIcon)

    if (duration > 0) {
      val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
      views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
      views.setTextViewText(R.id.widget_time_current, formatDuration(currentPosition))
      views.setTextViewText(R.id.widget_time_duration, formatDuration(duration))
    } else {
      views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
      views.setTextViewText(R.id.widget_time_current, "0:00")
      views.setTextViewText(R.id.widget_time_duration, "0:00")
    }

    views.setOnClickPendingIntent(R.id.widget_album_art, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_song_title, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_artist_name, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
    views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
    views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())
    views.setOnClickPendingIntent(R.id.widget_cast_button, getOutputSwitcherIntent())

    return views
  }

  private fun createVinylSquareRemoteViews(
    title: String,
    artist: String,
    vinylDisc: Bitmap,
    isPlaying: Boolean,
    duration: Long,
    currentPosition: Long,
    accentColor: Int,
    vinylAngle: Float
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_vinyl_square)

    views.setTextViewText(R.id.widget_song_title, title)
    views.setTextViewText(R.id.widget_artist_name, artist)
    views.setImageViewBitmap(R.id.widget_vinyl_disc, vinylDisc)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      views.setFloat(R.id.widget_vinyl_disc, "setRotation", vinylAngle)
      views.setFloat(R.id.widget_tonearm, "setRotation", if (isPlaying) 0f else -18f)
    }

    val playIcon = if (isPlaying) R.drawable.ic_widget_pause_bold_white else R.drawable.ic_widget_play_bold_white
    views.setImageViewResource(R.id.widget_play_pause, playIcon)

    if (duration > 0) {
      val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
      views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
      views.setTextViewText(R.id.widget_time_current, formatDuration(currentPosition))
      views.setTextViewText(R.id.widget_time_duration, formatDuration(duration))
    } else {
      views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
      views.setTextViewText(R.id.widget_time_current, "0:00")
      views.setTextViewText(R.id.widget_time_duration, "0:00")
    }

    views.setOnClickPendingIntent(R.id.widget_vinyl_container, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_vinyl_disc, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_song_title, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_artist_name, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
    views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
    views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())

    return views
  }

  private fun createVintageTurntableRemoteViews(
    title: String,
    artist: String,
    vintageVinylDisc: Bitmap,
    isPlaying: Boolean,
    duration: Long,
    currentPosition: Long,
    vinylAngle: Float
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_vintage_turntable)

    views.setTextViewText(R.id.widget_song_title, title)
    views.setTextViewText(R.id.widget_artist_name, artist)
    views.setImageViewBitmap(R.id.widget_vinyl_disc, vintageVinylDisc)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      views.setFloat(R.id.widget_vinyl_disc, "setRotation", vinylAngle)
      views.setFloat(R.id.widget_tonearm, "setRotation", if (isPlaying) 0f else -18f)
    }

    val playIcon = if (isPlaying) R.drawable.ic_widget_pause_brown else R.drawable.ic_widget_play_brown
    views.setImageViewResource(R.id.widget_play_pause, playIcon)

    if (duration > 0) {
      val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt().coerceIn(0, 10000)
      views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
      views.setTextViewText(R.id.widget_time_current, formatDuration(currentPosition))
      views.setTextViewText(R.id.widget_time_duration, formatDuration(duration))
    } else {
      views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
      views.setTextViewText(R.id.widget_time_current, "0:00")
      views.setTextViewText(R.id.widget_time_duration, "0:00")
    }

    views.setOnClickPendingIntent(R.id.widget_vinyl_disc, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_song_title, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_artist_name, getNowPlayingIntent())
    views.setOnClickPendingIntent(R.id.widget_prev_button, getPreviousIntent())
    views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
    views.setOnClickPendingIntent(R.id.widget_next_button, getNextIntent())

    return views
  }

  private fun getOpenAppIntent(): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun createVinylDiscBitmap(
    centerArt: Bitmap?,
    rotationDegrees: Float,
    discSize: Int = 300,
    hasShadow: Boolean = false
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(discSize, discSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = discSize / 2f
    val radius = center - if (hasShadow) 14f else 8f

    if (hasShadow) {
      val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30000000.toInt()
      }
      canvas.drawCircle(center + 2f, center + 4f, radius, shadowPaint)
    }

    canvas.save()
    canvas.rotate(rotationDegrees, center, center)

    // Outer Vinyl Record Disc (Glossy deep obsidian)
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF141517.toInt()
      style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radius, discPaint)

    // Concentric Grooves
    val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.2f
      color = 0x22FFFFFF.toInt()
    }
    val minGroove = radius * 0.48f
    val maxGroove = radius * 0.94f
    var r = minGroove
    while (r < maxGroove) {
      canvas.drawCircle(center, center, r, groovePaint)
      r += (radius * 0.05f)
    }

    // Specular light sheen highlight sweep across vinyl
    val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = SweepGradient(
        center, center,
        intArrayOf(0x00FFFFFF, 0x1EFFFFFF.toInt(), 0x00FFFFFF, 0x1EFFFFFF.toInt(), 0x00FFFFFF),
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
      )
      style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, radius, sheenPaint)

    // Center circular album artwork label
    val labelRadius = radius * 0.44f
    if (centerArt != null) {
      val circularArt = getCircularBitmap(centerArt)
      val src = Rect(0, 0, circularArt.width, circularArt.height)
      val dst = RectF(center - labelRadius, center - labelRadius, center + labelRadius, center + labelRadius)
      val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      canvas.drawBitmap(circularArt, src, dst, artPaint)
    } else {
      val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7C3AED.toInt()
      }
      canvas.drawCircle(center, center, labelRadius, fallbackPaint)
    }

    // Outer center ring rim
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f
      color = 0x55FFFFFF.toInt()
    }
    canvas.drawCircle(center, center, labelRadius, rimPaint)

    // Center spindle hole
    val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = 0xFF0D0E10.toInt()
    }
    val holeBezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f
      color = 0x99D1D5DB.toInt()
    }
    val holeRadius = radius * 0.075f
    canvas.drawCircle(center, center, holeRadius, holePaint)
    canvas.drawCircle(center, center, holeRadius, holeBezelPaint)

    canvas.restore()
    return bitmap
  }

  private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
    val size = minOf(bitmap.width, bitmap.height)

    // First crop to square
    val xOffset = (bitmap.width - size) / 2
    val yOffset = (bitmap.height - size) / 2
    val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint =
      Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
      }
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, paint)

    if (squareBitmap != bitmap) {
      squareBitmap.recycle()
    }
    return output
  }

  private fun extractAccentColor(bitmap: Bitmap?): Int {
    if (bitmap == null) return 0xFFA855F7.toInt()
    return try {
      val palette = Palette.from(bitmap).maximumColorCount(16).generate()
      palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: 0xFFA855F7.toInt()
    } catch (e: Exception) {
      0xFFA855F7.toInt()
    }
  }

  private suspend fun loadAlbumArt(artworkUri: String, size: Int = 200): Bitmap? {
    return withContext(Dispatchers.IO) {
      try {
        val request =
          ImageRequest.Builder(context)
            .data(artworkUri)
            .size(size, size)
            .allowHardware(false)
            .crossfade(300)
            .build()
        val result = imageLoader.execute(request)
        result.image?.toBitmap()
      } catch (e: Exception) {
        null
      }
    }
  }

  companion object {
    @Volatile private var instance: SkinWidgetManager? = null

    fun get(context: Context): SkinWidgetManager =
      instance ?: synchronized(this) {
        instance ?: SkinWidgetManager(context.applicationContext).also { instance = it }
      }
  }
}
