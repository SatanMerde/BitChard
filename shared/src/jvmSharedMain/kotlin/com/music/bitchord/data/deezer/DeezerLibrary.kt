package com.music.bitchord.data.deezer

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
 * Client for fetching Deezer user playlists, favorite tracks and followed artists.
 */
object DeezerLibrary {

    private const val API_BASE = "https://api.deezer.com"

    @Volatile
    var arlTokenProvider: (() -> String)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getArl(): String = arlTokenProvider?.invoke().orEmpty()

    /**
     * Fetches Deezer user playlists.
     */
    suspend fun playlists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val arl = getArl()
        if (arl.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/user/me/playlists")
            .addHeader("Cookie", "arl=$arl")
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = obj["title"]?.jsonPrimitive?.content ?: "Playlist"
                    val picture = obj["picture_medium"]?.jsonPrimitive?.content
                        ?: obj["picture_small"]?.jsonPrimitive?.content
                    val creator = obj["creator"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    SpotifyPlaylist(id = id, name = title, owner = creator, imageUrl = picture)
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches Deezer favorite / liked tracks (the user's "Coups de cœur").
     */
    suspend fun likedTracks(): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val arl = getArl()
        if (arl.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/user/me/tracks?limit=200")
            .addHeader("Cookie", "arl=$arl")
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown"
                    val albumObj = obj["album"]?.jsonObject
                    val album = albumObj?.get("title")?.jsonPrimitive?.content
                    val picture = albumObj?.get("cover_medium")?.jsonPrimitive?.content
                    val duration = obj["duration"]?.jsonPrimitive?.content?.toIntOrNull()?.times(1000) ?: 0
                    SpotifyTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        imageUrl = picture,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches tracks for a Deezer playlist by its ID.
     */
    suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val arl = getArl()
        val req = Request.Builder()
            .url("$API_BASE/playlist/$playlistId/tracks?limit=300")
            .apply {
                if (arl.isNotBlank()) addHeader("Cookie", "arl=$arl")
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
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Unknown"
                    val albumObj = obj["album"]?.jsonObject
                    val album = albumObj?.get("title")?.jsonPrimitive?.content
                    val picture = albumObj?.get("cover_medium")?.jsonPrimitive?.content
                    val duration = obj["duration"]?.jsonPrimitive?.content?.toIntOrNull()?.times(1000) ?: 0
                    SpotifyTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        imageUrl = picture,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetches followed artists from Deezer.
     */
    suspend fun followedArtists(): List<SpotifyArtist> = withContext(Dispatchers.IO) {
        val arl = getArl()
        if (arl.isBlank()) return@withContext emptyList()

        val req = Request.Builder()
            .url("$API_BASE/user/me/artists?limit=100")
            .addHeader("Cookie", "arl=$arl")
            .build()

        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body).jsonObject
                val data = parsed["data"]?.jsonArray ?: return@withContext emptyList()
                data.mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val picture = obj["picture_medium"]?.jsonPrimitive?.content
                    SpotifyArtist(id = id, name = name, imageUrl = picture)
                }
            }
        }.getOrDefault(emptyList())
    }
}
