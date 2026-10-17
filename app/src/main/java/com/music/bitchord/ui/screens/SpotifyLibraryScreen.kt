package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.spotify.SpotifyImporter
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The signed-in Spotify account's playlists. A tap opens one as an ordinary
 * playlist page ([onOpenPlaylist]) — this screen is only the way in.
 */
@Composable
fun SpotifyLibraryScreen(
    onOpenPlaylist: (SpotifyPlaylist) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenTokenSetup: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: MainViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val cookie by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBatchDialog by remember { mutableStateOf(false) }
    var importPlaylists by remember { mutableStateOf(true) }
    var importArtists by remember { mutableStateOf(true) }
    var saveToYtm by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var detailMessage by remember { mutableStateOf("") }
    var importDone by remember { mutableStateOf(false) }
    var summaryMessage by remember { mutableStateOf("") }
    var cancelRequested by remember { mutableStateOf(false) }

    LaunchedEffect(cookie) {
        if (cookie.isBlank()) {
            playlists = emptyList()
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess { playlists = it }
            .onFailure { error = it.message }
        loading = false
    }

    if (showBatchDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) showBatchDialog = false
            },
            title = {
                Text(
                    text = if (importDone) {
                        stringResource(R.string.spotify_batch_import_done)
                    } else if (isImporting) {
                        stringResource(R.string.spotify_batch_import_start)
                    } else {
                        stringResource(R.string.spotify_batch_import_dialog_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isImporting) {
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                        if (statusMessage.isNotBlank()) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (detailMessage.isNotBlank()) {
                            Text(
                                text = detailMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (importDone) {
                        Text(
                            text = summaryMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { importPlaylists = !importPlaylists }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = importPlaylists,
                                onCheckedChange = { importPlaylists = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.spotify_batch_import_playlists, playlists.size),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { importArtists = !importArtists }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = importArtists,
                                onCheckedChange = { importArtists = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.spotify_batch_import_artists),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (viewModel?.signedIn?.value == true) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { saveToYtm = false }
                                    .padding(vertical = 2.dp),
                            ) {
                                RadioButton(
                                    selected = !saveToYtm,
                                    onClick = { saveToYtm = false },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.spotify_batch_import_save_local),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { saveToYtm = true }
                                    .padding(vertical = 2.dp),
                            ) {
                                RadioButton(
                                    selected = saveToYtm,
                                    onClick = { saveToYtm = true },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.spotify_batch_import_save_ytm),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (importDone) {
                    Button(onClick = { showBatchDialog = false }) {
                        Text(stringResource(R.string.done))
                    }
                } else if (isImporting) {
                    OutlinedButton(onClick = { cancelRequested = true }) {
                        Text(stringResource(R.string.cancel))
                    }
                } else {
                    Button(
                        enabled = importPlaylists || importArtists,
                        onClick = {
                            isImporting = true
                            cancelRequested = false
                            importProgress = 0f
                            scope.launch {
                                var importedPCount = 0
                                var importedACount = 0
                                try {
                                    val list = if (playlists.isNotEmpty()) playlists else SpotifyLibrary.playlists()
                                    val totalSteps = (if (importPlaylists) list.size else 0) + (if (importArtists) 1 else 0)
                                    var currentStep = 0

                                    if (importPlaylists) {
                                        for ((pIdx, p) in list.withIndex()) {
                                            if (cancelRequested) break
                                            statusMessage = "${p.name} (${pIdx + 1}/${list.size})"
                                            detailMessage = context.getString(R.string.spotify_import_fetching)
                                            val tracks = runCatching { SpotifyLibrary.tracks(p.id) }.getOrNull().orEmpty()
                                            if (tracks.isNotEmpty()) {
                                                val songs = mutableListOf<Song>()
                                                for ((tIdx, track) in tracks.withIndex()) {
                                                    if (cancelRequested) break
                                                    detailMessage = "${track.title} (${tIdx + 1}/${tracks.size})"
                                                    val matched = runCatching { SpotifyImporter.matchTrack(track) }.getOrNull()
                                                    if (matched != null) songs.add(matched)
                                                }
                                                if (songs.isNotEmpty()) {
                                                    if (saveToYtm && viewModel?.signedIn?.value == true) {
                                                        viewModel.createPlaylistWithVideoIds(
                                                            title = p.name,
                                                            privacy = PlaylistPrivacy.PRIVATE,
                                                            videoIds = songs.map { it.videoId },
                                                            songs = songs,
                                                        )
                                                    } else {
                                                        LocalPlaylistStore.savePlaylist(p.name, songs)
                                                    }
                                                    importedPCount++
                                                }
                                            }
                                            currentStep++
                                            importProgress = currentStep.toFloat() / totalSteps.coerceAtLeast(1)
                                        }
                                    }

                                    if (importArtists && !cancelRequested) {
                                        statusMessage = context.getString(R.string.spotify_batch_import_artists)
                                        detailMessage = "..."
                                        val artists = runCatching { SpotifyLibrary.followedArtists() }.getOrNull().orEmpty()
                                        for ((aIdx, artist) in artists.withIndex()) {
                                            if (cancelRequested) break
                                            detailMessage = "${artist.name} (${aIdx + 1}/${artists.size})"
                                            val searchResult = YtMusicRepository.search(artist.name, SearchFilter.ARTISTS).getOrNull()
                                            val browse = searchResult?.filterIsInstance<SearchResult.Browse>()?.firstOrNull()?.item
                                            if (browse != null) {
                                                if (viewModel?.signedIn?.value == true) {
                                                    runCatching { YtMusicRepository.setSubscribed(browse.browseId, true) }
                                                }
                                                importedACount++
                                            }
                                        }
                                        currentStep++
                                        importProgress = 1f
                                    }

                                    summaryMessage = context.getString(
                                        R.string.spotify_batch_import_summary,
                                        importedPCount,
                                        importedACount,
                                    )
                                    importDone = true
                                    viewModel?.loadPlaylists()
                                    viewModel?.onLibraryShown()
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isImporting = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.spotify_batch_import_start))
                    }
                }
            },
            dismissButton = {
                if (!isImporting && !importDone) {
                    TextButton(onClick = { showBatchDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.spotify_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.spotify),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (cookie.isBlank()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.spotify_connect_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenLogin) {
                        Text(stringResource(R.string.spotify_sign_in))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenTokenSetup) {
                        Text(stringResource(R.string.spotify_use_spdc_token))
                    }
                }
            }
        } else {
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.spotify_batch_import_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.spotify_batch_import_card_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    importDone = false
                                    showBatchDialog = true
                                },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text(stringResource(R.string.spotify_batch_import_button))
                            }
                        }
                    }
                }
            }
            error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(playlists, key = { it.id }) { item ->
                LibraryRow(
                    title = item.name,
                    subtitle = item.owner.orEmpty(),
                    imageUrl = item.imageUrl,
                    onClick = { onOpenPlaylist(item) },
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
