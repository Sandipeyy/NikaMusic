// "Recognize" tab: a page of its own like Home and Search. Uses the app's existing Shazam engine and history table.
package com.sandip.nikamusic.ui.screens.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.shazamkit.models.RecognitionResult
import com.music.shazamkit.models.RecognitionStatus
import com.sandip.nikamusic.LocalDatabase
import com.sandip.nikamusic.R
import com.sandip.nikamusic.db.entities.RecognitionHistory
import com.sandip.nikamusic.recognition.MusicRecognitionService
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val CardColor = Color.White.copy(alpha = 0.08f)
private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@Composable
fun RecognitionScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val status by MusicRecognitionService.recognitionStatus.collectAsState()
    val history by database.recognitionHistory().collectAsState(initial = emptyList())
    val busy = status is RecognitionStatus.Listening || status is RecognitionStatus.Processing

    LaunchedEffect(Unit) { if (!busy) MusicRecognitionService.reset() }
    DisposableEffect(Unit) { onDispose { MusicRecognitionService.reset() } }

    fun startListening() {
        if (busy) return
        scope.launch {
            val outcome = MusicRecognitionService.recognize(context.applicationContext)
            if (outcome is RecognitionStatus.Success) {
                val r = outcome.result
                database.query {
                    insert(
                        RecognitionHistory(
                            trackId = r.trackId, title = r.title, artist = r.artist, album = r.album,
                            coverArtUrl = r.coverArtUrl, coverArtHqUrl = r.coverArtHqUrl, genre = r.genre,
                            releaseDate = r.releaseDate, label = r.label, shazamUrl = r.shazamUrl,
                            appleMusicUrl = r.appleMusicUrl, spotifyUrl = r.spotifyUrl, isrc = r.isrc,
                            youtubeVideoId = r.youtubeVideoId,
                        ),
                    )
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }
    val onMicClick = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun search(title: String, artist: String) {
        navController.navigate("search/${URLEncoder.encode("$title $artist".trim(), "UTF-8")}")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                "Recognize music",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
            MicButton(busy = busy, onClick = onMicClick)
            Spacer(Modifier.height(16.dp))
            Text(
                text = when (val s = status) {
                    RecognitionStatus.Ready -> "Tap to listen"
                    RecognitionStatus.Listening -> "Listening…"
                    RecognitionStatus.Processing -> "Identifying…"
                    is RecognitionStatus.Success -> "Found it"
                    is RecognitionStatus.NoMatch -> s.message
                    is RecognitionStatus.Error -> s.message
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (status is RecognitionStatus.Error) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(20.dp))
        }

        (status as? RecognitionStatus.Success)?.let { found ->
            item {
                ResultCard(found.result, onFind = { search(found.result.title, found.result.artist) }, onAgain = { startListening() })
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = { database.query { clearRecognitionHistory() } }) { Text("Clear") }
                }
            }
            if (history.isEmpty()) {
                Text(
                    "Songs you identify will show up here.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(history, key = { it.id }) { entry ->
            HistoryRow(entry, onClick = { search(entry.title, entry.artist) })
        }
        item { Spacer(Modifier.height(170.dp)) }
    }
}

@Composable
private fun MicButton(busy: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "recognizePulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
        if (busy) {
            Box(Modifier.size(190.dp).scale(pulse).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = !busy, onClick = onClick),
        ) {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = "Recognize music",
                tint = Color.Black,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@Composable
private fun ResultCard(result: RecognitionResult, onFind: () -> Unit, onAgain: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardColor).padding(20.dp),
    ) {
        AsyncImage(
            model = result.coverArtHqUrl ?: result.coverArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.1f)),
        )
        Spacer(Modifier.height(14.dp))
        Text(result.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(result.artist, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOfNotNull(result.album, result.genre, result.releaseDate?.take(4)).takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it.joinToString(" • "), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onFind) { Text("Find in NikaMusic") }
            TextButton(onClick = onAgain) { Text("Listen again") }
        }
    }
}

@Composable
private fun HistoryRow(entry: RecognitionHistory, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(CardColor).clickable(onClick = onClick).padding(10.dp),
    ) {
        AsyncImage(
            model = entry.coverArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(entry.recognizedAt.format(TimeFormat), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
    }
}
