package com.sandip.nikamusic.listentogether

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The profile picture as a tiny JPEG (base64), small enough to travel with every member list. */
object ListenAvatar {
    private const val Size = 64
    private const val MaxChars = 3600

    suspend fun encode(context: Context, uriString: String): String? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= Size * 2 && bounds.outHeight / (sample * 2) >= Size * 2) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            val small = Bitmap.createScaledBitmap(square, Size, Size, true)
            var quality = 70
            while (quality >= 30) {
                val out = ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val text = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (text.length <= MaxChars) return@runCatching text
                quality -= 10
            }
            null
        }.getOrNull()
    }
}
