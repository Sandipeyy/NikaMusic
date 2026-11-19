// The Listen Together page: start or join a session, see who is in it, what is playing and what is next.
// Opened from the profile card, the player menu and invite links (they all go through ListenTogetherController.requestOpenPage()).
package com.sandip.nikamusic.ui.screens.listentogether

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.sandip.nikamusic.LocalPlayerConnection
import com.sandip.nikamusic.R
import com.sandip.nikamusic.constants.ProfilePicUriKey
import com.sandip.nikamusic.constants.UsernameKey
import com.sandip.nikamusic.listentogether.ListenAvatar
import com.sandip.nikamusic.listentogether.ListenMember
import com.sandip.nikamusic.listentogether.ListenPhase
import com.sandip.nikamusic.listentogether.ListenRole
import com.sandip.nikamusic.listentogether.ListenTogetherConfig
import com.sandip.nikamusic.listentogether.ListenTogetherController
import com.sandip.nikamusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardColor = Color.White.copy(alpha = 0.08f)
private val Muted = Color.White.copy(alpha = 0.6f)
private val Live = Color(0xFF4ADE80)
private val Warn = Color(0xFFFBBF24)
private val Danger = Color(0xFFF87171)

@Composable
fun ListenTogetherScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by ListenTogetherController.ui.collectAsState()
    val pc = LocalPlayerConnection.current

    // who you are in a room comes from your profile: nothing to fill in
    val (username) = rememberPreference(UsernameKey, "")
    val (profilePic) = rememberPreference(ProfilePicUriKey, "")
    val displayName = username.trim().ifBlank { "Guest" }
    var code by remember { mutableStateOf("") }
    val busy = ui.phase == ListenPhase.CONNECTING

    // an invite link fills the code in; nothing is joined until the person taps Join
    LaunchedEffect(ui.pendingInvite) { ui.pendingInvite?.let { code = it.code } }

    fun withAvatar(action: (avatar: String?) -> Unit) {
        scope.launch { action(ListenAvatar.encode(context, profilePic)) }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topInset + 74.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Text("Listen together", fontSize = 30.sp, fontWeight = FontWeight.Bold) }

            ui.message?.let { message ->
                item { NoticeCard(message, onDismiss = { ListenTogetherController.clearMessage() }) }
            }

            if (!ui.inSession) {
                item { IntroCard() }
                if (!ListenTogetherConfig.available) {
                    item {
                        Card {
                            Text("Not available right now", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Listen together can't connect at the moment. Please try again later.", color = Muted, fontSize = 14.sp)
                        }
                    }
                } else {
                    ui.pendingInvite?.let { invite ->
                        item {
                            Card {
                                Text("You were invited", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Room ${invite.code}", color = Muted, fontSize = 14.sp)
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        withAvatar { avatar ->
                                            ListenTogetherController.clearInvite()
                                            ListenTogetherController.join(displayName, avatar, invite.code)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (busy) "Joining…" else "Join room") }
                            }
                        }
                    }
                    item {
                        Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatar(displayName, null, 44.dp, profilePic)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Listening as", color = Muted, fontSize = 12.sp)
                                    Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                    item {
                        Card {
                            Text("Create a room", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("You pick the music. Everyone who joins hears it in sync.", color = Muted, fontSize = 14.sp)
                            Button(
                                enabled = !busy,
                                onClick = { withAvatar { avatar -> ListenTogetherController.host(displayName, avatar) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (busy) "Connecting…" else "Create room") }
                        }
                    }
                    item {
                        Card {
                            Text("Join a room", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                                    singleLine = true,
                                    label = { Text("Room code") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    enabled = !busy,
                                )
                                TextButton(enabled = !busy, onClick = { pasteCode(context)?.let { code = it } }) { Text("Paste") }
                            }
                            Button(
                                enabled = !busy && code.length == 6,
                                onClick = { withAvatar { avatar -> ListenTogetherController.join(displayName, avatar, code) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Join room") }
                        }
                    }
                    if (busy) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Connecting… this can take a moment the first time.", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                item { SessionCard(ui.code, ui.phase, ui.role, ui.members.size, onInvite = { shareInvite(context) }, onCopy = { copyText(context, ui.code) }) }
                item { MembersCard(ui.members, ui.you, ui.role == ListenRole.HOST) }
                item { NowPlayingCard(ui.role, pc, ui.nowPlaying) }
                item { QueueCard(ui) }
                item {
                    OutlinedButton(
                        onClick = { ListenTogetherController.leave() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (ui.role == ListenRole.HOST) "End room for everyone" else "Leave room") }
                }
            }
        }

        // the same round back button the other pages have
        Box(
            modifier = Modifier
                .padding(top = topInset + 14.dp, start = 18.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                .clickable { navController.navigateUp() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.arrow_back_ios), "Back", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardColor).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun IntroCard() {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.group), null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("Same music, same moment", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Text(
            "Create a room and share the code or invite link. Everyone who joins hears what you play, in sync: play, pause, skip and seek.",
            color = Muted, fontSize = 14.sp,
        )
    }
}

@Composable
private fun NoticeCard(message: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Warn.copy(alpha = 0.16f)).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(message, modifier = Modifier.weight(1f), fontSize = 14.sp)
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

@Composable
private fun SessionCard(code: String, phase: ListenPhase, role: ListenRole, count: Int, onInvite: () -> Unit, onCopy: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val reconnecting = phase == ListenPhase.RECONNECTING
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (reconnecting) Warn else Live))
            Spacer(Modifier.width(8.dp))
            Text(if (reconnecting) "Reconnecting…" else "Live", fontWeight = FontWeight.SemiBold, color = if (reconnecting) Warn else Live)
            Spacer(Modifier.weight(1f))
            Text(if (role == ListenRole.HOST) "You are the host" else "Following the host", color = Muted, fontSize = 13.sp)
        }
        if (phase == ListenPhase.RECONNECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Room code", color = Muted, fontSize = 12.sp)
        Text(code, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
        Text("$count in this room", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onInvite, modifier = Modifier.weight(1f)) { Text("Invite friends") }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Copy code") }
        }
    }
}

@Composable
private fun MembersCard(members: List<ListenMember>, you: String, isHost: Boolean) {
    Card {
        Text("In the room", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MemberAvatar(member.name, member.avatar, 40.dp, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (member.isHost) Tag("Host")
                        if (member.id == you) Tag("You")
                    }
                    Text(if (member.online) "Online" else "Reconnecting…", color = if (member.online) Live else Warn, fontSize = 12.sp)
                }
                // only the host can remove people, and never themselves
                if (isHost && member.id != you && !member.isHost) {
                    TextButton(
                        onClick = { ListenTogetherController.kick(member.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Danger),
                    ) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun NowPlayingCard(role: ListenRole, pc: com.sandip.nikamusic.playback.PlayerConnection?, remote: com.sandip.nikamusic.listentogether.RemotePlayback?) {
    val local = pc?.mediaMetadata?.collectAsState()?.value
    val host = role == ListenRole.HOST
    val id = if (host) local?.id else remote?.songId
    val title = if (host) local?.title else remote?.title
    val artist = if (host) local?.artists?.joinToString { it.name } else remote?.artist
    Card {
        Text("Now playing", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (id == null) {
            Text(if (host) "Play something and everyone hears it." else "Waiting for the host to play something…", color = Muted, fontSize = 14.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/$id/mqdefault.jpg", contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title.orEmpty(), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artist.orEmpty(), color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (host && pc != null) HostControls(pc) else OutlinedButton(onClick = { ListenTogetherController.resync() }, modifier = Modifier.fillMaxWidth()) { Text("Resync with the host") }
        }
    }
}

@Composable
private fun HostControls(pc: com.sandip.nikamusic.playback.PlayerConnection) {
    val playing by produceState(false, pc) {
        while (true) {
            value = runCatching { pc.player.playWhenReady }.getOrDefault(false)
            delay(400)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { runCatching { pc.player.seekToPrevious() } }, modifier = Modifier.weight(1f)) { Text("Previous") }
        Button(onClick = { runCatching { pc.player.playWhenReady = !pc.player.playWhenReady } }, modifier = Modifier.weight(1f)) { Text(if (playing) "Pause" else "Play") }
        OutlinedButton(onClick = { runCatching { pc.player.seekToNext() } }, modifier = Modifier.weight(1f)) { Text("Next") }
    }
}

@Composable
private fun QueueCard(ui: com.sandip.nikamusic.listentogether.ListenUiState) {
    Card {
        Text("Up next", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        val next = ui.queue.drop(1)
        if (next.isEmpty()) {
            Text("The queue is empty.", color = Muted, fontSize = 14.sp)
        } else {
            next.take(20).forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${index + 1}", color = Muted, modifier = Modifier.width(26.dp), fontSize = 13.sp)
                    AsyncImage(
                        model = "https://i.ytimg.com/vi/${entry.id}/default.jpg", contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        Text(entry.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (next.size > 20) Text("and ${next.size - 20} more", color = Muted, fontSize = 12.sp)
        }
        if (ui.role == ListenRole.GUEST) {
            Text("To add a song, open its menu and choose “Add to room queue”. The host's app adds it.", color = Muted, fontSize = 12.sp)
        }
    }
}

private val AvatarColors = listOf(0xFF6366F1, 0xFFEC4899, 0xFF14B8A6, 0xFFF59E0B, 0xFF8B5CF6, 0xFF22C55E, 0xFFEF4444, 0xFF0EA5E9)

/** A member's picture, or their initial on a colour picked from their name. [localUri] is this device's own profile picture. */
@Composable
private fun MemberAvatar(name: String, avatarBase64: String?, size: Dp, localUri: String?) {
    val bitmap = remember(avatarBase64) {
        avatarBase64?.let {
            runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val color = Color(AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size])
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (!localUri.isNullOrBlank()) {
            AsyncImage(localUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun shareInvite(context: Context) {
    val text = ListenTogetherController.inviteText()
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Invite friends").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Room code", text))
}

/** A 6-character code from the clipboard: either the bare code or the one inside an invite link. */
private fun pasteCode(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim() ?: return null
    runCatching { Uri.parse(text).getQueryParameter("code") }.getOrNull()?.trim()?.uppercase()?.takeIf { it.length == 6 }?.let { return it }
    return Regex("[A-Za-z0-9]{6}").find(text)?.value?.uppercase()
}
