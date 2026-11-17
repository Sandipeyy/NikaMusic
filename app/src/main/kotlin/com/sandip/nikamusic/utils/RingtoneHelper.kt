// Idea and UI flow ported from Echo Music (GPL-3.0, https://github.com/iad1tya/Echo-Music).
// Unlike upstream, this version really trims the audio (AAC samples are cut by timestamp, no re-encode).
package com.sandip.nikamusic.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.getSystemService
import com.sandip.nikamusic.constants.AudioQuality
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RingtoneHelper {
    /** Saving into the shared Ringtones folder without storage permission needs scoped storage (Android 10+). */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    data class Stream(val url: String, val headers: Map<String, String>)

    /** MEDIUM resolves to YouTube's ~128 kbps AAC (m4a) stream, which can be cut without re-encoding. */
    suspend fun resolveStream(context: Context, songId: String): Stream? = withContext(Dispatchers.IO) {
        runCatching {
            val cm = context.getSystemService<ConnectivityManager>() ?: return@runCatching null
            YTPlayerUtils.playerResponseForPlayback(
                videoId = songId,
                audioQuality = AudioQuality.MEDIUM,
                connectivityManager = cm,
            ).getOrNull()?.let { Stream(it.streamUrl, it.streamHeaders) }
        }.getOrNull()
    }

    suspend fun downloadAndTrimAsRingtone(
        context: Context,
        songId: String,
        title: String,
        artist: String,
        startMs: Long,
        endMs: Long,
        onProgress: (Float, String) -> Unit,
        onComplete: (Boolean, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val source = File(context.cacheDir, "ringtone_source_$songId")
        val trimmed = File(context.cacheDir, "ringtone_trimmed_$songId.m4a")
        try {
            if (!isSupported) {
                onComplete(false, "Setting a ringtone needs Android 10 or newer")
                return@withContext
            }
            onProgress(0.05f, "Getting audio stream...")
            val stream = resolveStream(context, songId)
            if (stream == null) {
                onComplete(false, "Failed to get the audio stream")
                return@withContext
            }

            onProgress(0.1f, "Downloading...")
            val connection = (URL(stream.url).openConnection() as HttpURLConnection).apply {
                stream.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    source.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var done = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress(0.1f + done.toFloat() / total * 0.5f, "Downloading...")
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (source.length() == 0L) {
                onComplete(false, "Download failed")
                return@withContext
            }

            onProgress(0.65f, "Cutting the selected part...")
            if (!trimAac(source, trimmed, startMs, endMs) || trimmed.length() == 0L) {
                onComplete(false, "Could not cut this audio format")
                return@withContext
            }

            onProgress(0.85f, "Saving to Ringtones...")
            val name = title.replace(Regex("[^A-Za-z0-9 ]"), "").trim().ifEmpty { "song" } + "_ringtone_$songId.m4a"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
                put(MediaStore.Audio.Media.TITLE, "$title (Ringtone)")
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the ringtone file")
            context.contentResolver.openOutputStream(uri)?.use { out -> trimmed.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

            onProgress(1f, "Done")
            onComplete(true, "\"$title\" was saved to your Ringtones. Choose it in Sound settings → Phone ringtone.")
        } catch (e: Exception) {
            onComplete(false, "Error: ${e.message ?: "unknown"}")
        } finally {
            source.delete()
            trimmed.delete()
        }
    }

    /** Copies the AAC samples between [startMs] and [endMs] into a new .m4a without re-encoding. */
    private fun trimAac(input: File, output: File, startMs: Long, endMs: Long): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            val format = extractor.getTrackFormat(track)
            if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) return false

            extractor.selectTrack(track)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstUs = -1L
            var written = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time > endUs) break
                if (firstUs < 0) firstUs = time
                info.set(0, size, time - firstUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                muxer.writeSampleData(outTrack, buffer, info)
                written++
                extractor.advance()
            }
            written > 0
        } catch (e: Exception) {
            false
        } finally {
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    fun openSoundSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
