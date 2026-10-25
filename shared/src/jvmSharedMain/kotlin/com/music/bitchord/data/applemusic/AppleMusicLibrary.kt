package com.music.bitchord.data.applemusic

import com.music.bitchord.data.Http
import com.music.bitchord.data.spotify.SpotifyArtist
import com.music.bitchord.data.spotify.SpotifyPlaylist
import com.music.bitchord.data.spotify.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * Client for querying Apple Music user library playlists, favorites, and artists.
 */
object AppleMusicLibrary {

    private const val API_BASE = "https://amp-api.music.apple.com/v1"

    @Volatile
    var userTokenProvider: (() -> String)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getToken(): String = userTokenProvider?.invoke().orEmpty()

    /**
     * Fetches playlists from the user's Apple Music cloud library.
     */
    suspend fun playlists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/me/library/playlists")
            .apply {
                if (token.startsWith("ey") || !token.contains("=")) {
                    addHeader("Music-User-Token", token)
                } else {
                    addHeader("Cookie", token)
                }
            }
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attrs = obj["attributes"]?.jsonObject
                    val name = attrs?.get("name")?.jsonPrimitive?.content ?: "Playlist"
                    val artwork = attrs?.get("artwork")?.jsonObject
                    val urlTemplate = artwork?.get("url")?.jsonPrimitive?.content
                    val imageUrl = urlTemplate?.replace("{w}", "300")?.replace("{h}", "300")
                    SpotifyPlaylist(id = id, name = name, owner = "Apple Music", imageUrl = imageUrl)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches tracks from an Apple Music library playlist.
     */
    suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/me/library/playlists/$playlistId/tracks")
            .apply {
                if (token.startsWith("ey") || !token.contains("=")) {
                    addHeader("Music-User-Token", token)
                } else {
                    addHeader("Cookie", token)
                }
            }
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attrs = obj["attributes"]?.jsonObject
                    val title = attrs?.get("name")?.jsonPrimitive?.content ?: return@mapNotNull null
                    val artist = attrs["artistName"]?.jsonPrimitive?.content ?: "Unknown"
                    val album = attrs["albumName"]?.jsonPrimitive?.content
                    val artwork = attrs["artwork"]?.jsonObject
                    val urlTemplate = artwork?.get("url")?.jsonPrimitive?.content
                    val imageUrl = urlTemplate?.replace("{w}", "300")?.replace("{h}", "300")
                    val durationMs = attrs["durationInMillis"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    SpotifyTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        imageUrl = imageUrl,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches favorite/liked tracks from Apple Music library songs.
     */
    suspend fun likedTracks(): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/me/library/songs?limit=100")
            .apply {
                if (token.startsWith("ey") || !token.contains("=")) {
                    addHeader("Music-User-Token", token)
                } else {
                    addHeader("Cookie", token)
                }
            }
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attrs = obj["attributes"]?.jsonObject
                    val title = attrs?.get("name")?.jsonPrimitive?.content ?: return@mapNotNull null
                    val artist = attrs["artistName"]?.jsonPrimitive?.content ?: "Unknown"
                    val album = attrs["albumName"]?.jsonPrimitive?.content
                    val artwork = attrs["artwork"]?.jsonObject
                    val urlTemplate = artwork?.get("url")?.jsonPrimitive?.content
                    val imageUrl = urlTemplate?.replace("{w}", "300")?.replace("{h}", "300")
                    val durationMs = attrs["durationInMillis"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    SpotifyTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        imageUrl = imageUrl,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches followed / library artists from Apple Music.
     */
    suspend fun followedArtists(): List<SpotifyArtist> = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/me/library/artists?limit=100")
            .apply {
                if (token.startsWith("ey") || !token.contains("=")) {
                    addHeader("Music-User-Token", token)
                } else {
                    addHeader("Cookie", token)
                }
            }
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attrs = obj["attributes"]?.jsonObject
                    val name = attrs?.get("name")?.jsonPrimitive?.content ?: return@mapNotNull null
                    val artwork = attrs["artwork"]?.jsonObject
                    val urlTemplate = artwork?.get("url")?.jsonPrimitive?.content
                    val imageUrl = urlTemplate?.replace("{w}", "300")?.replace("{h}", "300")
                    SpotifyArtist(id = id, name = name, imageUrl = imageUrl)
                }
            }
        }.getOrDefault(emptyList())
    }
}
