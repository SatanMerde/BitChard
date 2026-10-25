package com.music.bitchord.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.spotify.SpotifyImporter
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.ui.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class ConnectedMusicService(val displayName: String, val color: Color) {
    SPOTIFY("Spotify", Color(0xFF1DB954)),
    DEEZER("Deezer", Color(0xFFFF0055)),
    APPLE_MUSIC("Apple Music", Color(0xFFFA2D48)),
    YOUTUBE_MUSIC("YouTube Music", Color(0xFFFF0000)),
}

/**
 * Universal Post-Login Library Import Dialog.
 * Triggered automatically upon connecting Spotify, Deezer, Apple Music or YouTube Music.
 * Lets the user import playlists, liked tracks, followed artists into local storage or YTM account.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AccountLibraryImportDialog(
    service: ConnectedMusicService,
    hazeState: HazeState,
    viewModel: MainViewModel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var importPlaylists by remember { mutableStateOf(true) }
    var importLikedTracks by remember { mutableStateOf(true) }
    var importFollowedArtists by remember { mutableStateOf(true) }
    var saveToYtm by remember { mutableStateOf(false) }

    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var detailMessage by remember { mutableStateOf("") }
    var importDone by remember { mutableStateOf(false) }
    var summaryMessage by remember { mutableStateOf("") }
    var cancelRequested by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = { if (!isImporting) onDismiss() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(shape)
                .optimizedHazeEffect(
                    state = hazeState,
                    style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = {},
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header icon badge & title
            Text(
                text = "✨ ${service.displayName}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = service.color,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (importDone) {
                    stringResource(R.string.spotify_batch_import_done)
                } else if (isImporting) {
                    stringResource(R.string.spotify_batch_import_start)
                } else {
                    "Importer votre bibliothèque ?"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.W700,
                    fontSize = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (importDone) {
                    summaryMessage
                } else if (isImporting) {
                    "Synchronisation en cours avec BitChard..."
                } else {
                    "Sélectionnez les contenus de votre compte ${service.displayName} à synchroniser :"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            if (isImporting) {
                LinearProgressIndicator(
                    progress = { importProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = service.color,
                )
                Spacer(Modifier.height(10.dp))
                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                if (detailMessage.isNotBlank()) {
                    Text(
                        text = detailMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            } else if (!importDone) {
                // Checkbox options
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                            text = "Toutes vos playlists",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { importLikedTracks = !importLikedTracks }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = importLikedTracks,
                            onCheckedChange = { importLikedTracks = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Titres likés & favoris",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { importFollowedArtists = !importFollowedArtists }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = importFollowedArtists,
                            onCheckedChange = { importFollowedArtists = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Artistes suivis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Storage destination if user is signed in to YTM
                if (viewModel?.signedIn?.value == true && service != ConnectedMusicService.YOUTUBE_MUSIC) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Destination :",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveToYtm = false },
                    ) {
                        RadioButton(selected = !saveToYtm, onClick = { saveToYtm = false })
                        Spacer(Modifier.width(6.dp))
                        Text("Enregistrer dans BitChard (Local)", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveToYtm = true },
                    ) {
                        RadioButton(selected = saveToYtm, onClick = { saveToYtm = true })
                        Spacer(Modifier.width(6.dp))
                        Text("Sauvegarder sur YouTube Music", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action buttons
            if (importDone) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.done))
                }
            } else if (isImporting) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { cancelRequested = true },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = importPlaylists || importLikedTracks || importFollowedArtists,
                    onClick = {
                        isImporting = true
                        cancelRequested = false
                        importProgress = 0f
                        scope.launch {
                            var importedPCount = 0
                            var importedACount = 0
                            try {
                                when (service) {
                                    ConnectedMusicService.SPOTIFY -> {
                                        val allPlaylists = runCatching { SpotifyLibrary.playlists() }.getOrNull().orEmpty()
                                        val filteredPlaylists = allPlaylists.filter { p ->
                                            if (p.id == SpotifyLibrary.LIKED_ID) importLikedTracks else importPlaylists
                                        }
                                        val totalSteps = filteredPlaylists.size + (if (importFollowedArtists) 1 else 0)
                                        var currentStep = 0

                                        for ((pIdx, p) in filteredPlaylists.withIndex()) {
                                            if (cancelRequested) break
                                            statusMessage = "${p.name} (${pIdx + 1}/${filteredPlaylists.size})"
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

                                        if (importFollowedArtists && !cancelRequested) {
                                            statusMessage = context.getString(R.string.spotify_batch_import_artists)
                                            detailMessage = "Recherche des artistes..."
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
                                    }
                                    ConnectedMusicService.YOUTUBE_MUSIC -> {
                                        statusMessage = "Synchronisation YouTube Music..."
                                        viewModel?.loadPlaylists()
                                        viewModel?.onLibraryShown()
                                        importedPCount = 1
                                    }
                                     ConnectedMusicService.DEEZER -> {
                                         val playlists = if (importPlaylists) {
                                             runCatching { com.music.bitchord.data.deezer.DeezerLibrary.playlists() }.getOrNull().orEmpty()
                                         } else emptyList()
                                         val liked = if (importLikedTracks) {
                                             runCatching { com.music.bitchord.data.deezer.DeezerLibrary.likedTracks() }.getOrNull().orEmpty()
                                         } else emptyList()
                                         val followed = if (importFollowedArtists) {
                                             runCatching { com.music.bitchord.data.deezer.DeezerLibrary.followedArtists() }.getOrNull().orEmpty()
                                         } else emptyList()

                                         val totalSteps = playlists.size + (if (liked.isNotEmpty()) 1 else 0) + (if (followed.isNotEmpty()) 1 else 0)
                                         var currentStep = 0

                                         if (liked.isNotEmpty() && !cancelRequested) {
                                             statusMessage = "Deezer : Titres likés"
                                             val songs = mutableListOf<Song>()
                                             for ((tIdx, track) in liked.withIndex()) {
                                                 if (cancelRequested) break
                                                 detailMessage = "${track.title} (${tIdx + 1}/${liked.size})"
                                                 val matched = runCatching { SpotifyImporter.matchTrack(track) }.getOrNull()
                                                 if (matched != null) songs.add(matched)
                                             }
                                             if (songs.isNotEmpty()) {
                                                 if (saveToYtm && viewModel?.signedIn?.value == true) {
                                                     viewModel.createPlaylistWithVideoIds("Deezer - Coups de cœur", PlaylistPrivacy.PRIVATE, songs.map { it.videoId }, songs)
                                                 } else {
                                                     LocalPlaylistStore.savePlaylist("Deezer - Coups de cœur", songs)
                                                 }
                                                 importedPCount++
                                             }
                                             currentStep++
                                             importProgress = currentStep.toFloat() / totalSteps.coerceAtLeast(1)
                                         }

                                         for ((pIdx, p) in playlists.withIndex()) {
                                             if (cancelRequested) break
                                             statusMessage = "${p.name} (${pIdx + 1}/${playlists.size})"
                                             val tracks = runCatching { com.music.bitchord.data.deezer.DeezerLibrary.playlistTracks(p.id) }.getOrNull().orEmpty()
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
                                                         viewModel.createPlaylistWithVideoIds(p.name, PlaylistPrivacy.PRIVATE, songs.map { it.videoId }, songs)
                                                     } else {
                                                         LocalPlaylistStore.savePlaylist(p.name, songs)
                                                     }
                                                     importedPCount++
                                                 }
                                             }
                                             currentStep++
                                             importProgress = currentStep.toFloat() / totalSteps.coerceAtLeast(1)
                                         }

                                         if (followed.isNotEmpty() && !cancelRequested) {
                                             statusMessage = "Artistes suivis Deezer"
                                             for ((aIdx, artist) in followed.withIndex()) {
                                                 if (cancelRequested) break
                                                 detailMessage = "${artist.name} (${aIdx + 1}/${followed.size})"
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
                                     }
                                     ConnectedMusicService.APPLE_MUSIC -> {
                                         val playlists = if (importPlaylists) {
                                             runCatching { com.music.bitchord.data.applemusic.AppleMusicLibrary.playlists() }.getOrNull().orEmpty()
                                         } else emptyList()
                                         val liked = if (importLikedTracks) {
                                             runCatching { com.music.bitchord.data.applemusic.AppleMusicLibrary.likedTracks() }.getOrNull().orEmpty()
                                         } else emptyList()
                                         val followed = if (importFollowedArtists) {
                                             runCatching { com.music.bitchord.data.applemusic.AppleMusicLibrary.followedArtists() }.getOrNull().orEmpty()
                                         } else emptyList()

                                         val totalSteps = playlists.size + (if (liked.isNotEmpty()) 1 else 0) + (if (followed.isNotEmpty()) 1 else 0)
                                         var currentStep = 0

                                         if (liked.isNotEmpty() && !cancelRequested) {
                                             statusMessage = "Apple Music : Morceaux favoris"
                                             val songs = mutableListOf<Song>()
                                             for ((tIdx, track) in liked.withIndex()) {
                                                 if (cancelRequested) break
                                                 detailMessage = "${track.title} (${tIdx + 1}/${liked.size})"
                                                 val matched = runCatching { SpotifyImporter.matchTrack(track) }.getOrNull()
                                                 if (matched != null) songs.add(matched)
                                             }
                                             if (songs.isNotEmpty()) {
                                                 if (saveToYtm && viewModel?.signedIn?.value == true) {
                                                     viewModel.createPlaylistWithVideoIds("Apple Music - Favoris", PlaylistPrivacy.PRIVATE, songs.map { it.videoId }, songs)
                                                 } else {
                                                     LocalPlaylistStore.savePlaylist("Apple Music - Favoris", songs)
                                                 }
                                                 importedPCount++
                                             }
                                             currentStep++
                                             importProgress = currentStep.toFloat() / totalSteps.coerceAtLeast(1)
                                         }

                                         for ((pIdx, p) in playlists.withIndex()) {
                                             if (cancelRequested) break
                                             statusMessage = "${p.name} (${pIdx + 1}/${playlists.size})"
                                             val tracks = runCatching { com.music.bitchord.data.applemusic.AppleMusicLibrary.playlistTracks(p.id) }.getOrNull().orEmpty()
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
                                                         viewModel.createPlaylistWithVideoIds(p.name, PlaylistPrivacy.PRIVATE, songs.map { it.videoId }, songs)
                                                     } else {
                                                         LocalPlaylistStore.savePlaylist(p.name, songs)
                                                     }
                                                     importedPCount++
                                                 }
                                             }
                                             currentStep++
                                             importProgress = currentStep.toFloat() / totalSteps.coerceAtLeast(1)
                                         }

                                         if (followed.isNotEmpty() && !cancelRequested) {
                                             statusMessage = "Artistes Apple Music"
                                             for ((aIdx, artist) in followed.withIndex()) {
                                                 if (cancelRequested) break
                                                 detailMessage = "${artist.name} (${aIdx + 1}/${followed.size})"
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
                                     }
                                }

                                summaryMessage = "$importedPCount playlists et $importedACount artistes synchronisés avec succès !"
                                importDone = true
                                viewModel?.loadPlaylists()
                                viewModel?.onLibraryShown()
                            } catch (cancelled: CancellationException) {
                                statusMessage = "Importation annulée."
                                isImporting = false
                            } catch (e: Exception) {
                                statusMessage = "Erreur: ${e.message}"
                                isImporting = false
                            }
                        }
                    },
                ) {
                    Text("Tout Importer")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                ) {
                    Text(stringResource(R.string.later))
                }
            }
        }
    }
}
