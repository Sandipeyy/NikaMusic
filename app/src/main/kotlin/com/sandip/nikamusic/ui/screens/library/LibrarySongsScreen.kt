// LibrarySongsScreen.kt

package com.sandip.nikamusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.sandip.nikamusic.LocalPlayerConnection
import com.sandip.nikamusic.R
import com.sandip.nikamusic.extensions.toMediaItem
import com.sandip.nikamusic.playback.queues.ListQueue
import com.sandip.nikamusic.ui.component.LocalMenuState
import com.sandip.nikamusic.ui.component.SongSwipeBox
import com.sandip.nikamusic.ui.component.librarySwipeAction
import com.sandip.nikamusic.ui.component.queueSwipeAction
import com.sandip.nikamusic.ui.menu.SongMenu
import com.sandip.nikamusic.viewmodels.LibraryHomeViewModel

@Composable
fun LibrarySongsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val songsTitle = stringResource(R.string.songs)

    LibraryEntityListScreen(
        title = songsTitle,
        subtitle = stringResource(R.string.lib_songs_subtitle),
        searchPlaceholder = stringResource(R.string.lib_songs_search),
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // Queue the alphabetical order the user is looking at, not the underlying
            // create-date order — otherwise tapping a row starts a queue that jumps somewhere
            // unrelated to what's on screen.
            val ordered = sortForLibraryIndex(songs) { it.song.title }
            playerConnection.playQueue(
                ListQueue(
                    title = songsTitle,
                    items = ordered.map { it.toMediaItem() },
                    startIndex = ordered.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        onLongClick = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        // left queues the song, right takes it out of the library (it's already in it)
        rowWrapper = { song, row ->
            SongSwipeBox(
                slab = false,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
    )
}
