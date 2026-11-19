// Dialog for the optional AI song insights. Mount once near the root.
package com.sandip.nikamusic.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sandip.nikamusic.MainActivity
import com.sandip.nikamusic.ai.AiSongController
import com.sandip.nikamusic.ai.AiSongInsights
import com.sandip.nikamusic.ai.InsightMode
import com.sandip.nikamusic.constants.OpenRouterModelKey
import com.sandip.nikamusic.utils.dataStore
import com.sandip.nikamusic.utils.get

@Composable
fun AiSongHost() {
    val state by AiSongController.state.collectAsState()
    if (!state.visible) return
    val context = LocalContext.current

    var hasKey by remember { mutableStateOf(true) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(AiSongInsights.DEFAULT_MODEL) }
    LaunchedEffect(Unit) {
        hasKey = AiSongInsights.hasKey(context)
        model = context.dataStore.get(OpenRouterModelKey, AiSongInsights.DEFAULT_MODEL).ifBlank { AiSongInsights.DEFAULT_MODEL }
    }

    AlertDialog(
        onDismissRequest = { AiSongController.close() },
        title = {
            Column {
                Text("Song insights", fontWeight = FontWeight.Bold)
                Text(
                    "${state.title} · ${state.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Optional. Nothing is sent until you tap a button, and only the song title and artist are shared with the AI service you set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (!hasKey) {
                    OutlinedTextField(
                        value = key, onValueChange = { key = it }, singleLine = true,
                        label = { Text("OpenRouter API key") }, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = model, onValueChange = { model = it }, singleLine = true,
                        label = { Text("Model") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A free key from openrouter.ai works. It is stored only on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = key.isNotBlank(),
                        onClick = { AiSongController.saveKey(context, key, model); hasKey = true },
                    ) { Text("Save key") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InsightChip("Meaning", state.mode == InsightMode.MEANING, !state.loading) { AiSongController.run(context, InsightMode.MEANING) }
                        InsightChip("References", state.mode == InsightMode.REFERENCES, !state.loading) { AiSongController.run(context, InsightMode.REFERENCES) }
                        InsightChip("Similar", state.mode == InsightMode.SIMILAR, !state.loading) { AiSongController.run(context, InsightMode.SIMILAR) }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    state.similar.forEach { song ->
                        Column(
                            Modifier.fillMaxWidth().clickable { openSearch(context, "${song.title} ${song.artist}") }.padding(vertical = 8.dp),
                        ) {
                            Text(song.title, fontWeight = FontWeight.SemiBold)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (song.why.isNotBlank()) Text(song.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.similar.isNotEmpty()) {
                        Text("Tap a song to search for it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AI can be wrong, especially about lesser-known songs. To translate the lyrics, use the translate option in the lyrics screen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { AiSongController.close() }) { Text("Close") } },
    )
}

@Composable
private fun InsightChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, enabled = enabled, onClick = onClick, label = { Text(label) })
}

private fun openSearch(context: Context, query: String) {
    AiSongController.close()
    val uri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
}
