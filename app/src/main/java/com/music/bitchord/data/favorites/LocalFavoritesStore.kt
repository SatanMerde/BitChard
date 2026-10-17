package com.music.bitchord.data.favorites

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 100% Offline Local Favorites (Liked Songs) store for BitChard.
 *
 * Allows users to like, heart, save, and manage their favorite songs locally
 * without requiring any Google account or internet connection.
 * Persisted in SharedPreferences as JSON and integrated seamlessly into
 * [LikeState], the Library tab, and full backup/export/import.
 */
object LocalFavoritesStore {
    private const val PREF_NAME = "bitchord_local_favorites"
    private const val KEY_FAVORITES = "favorites_json"

    private lateinit var prefs: SharedPreferences

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    fun init(context: Context) {
        if (!this::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            load()
        }
    }

    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return
        runCatching {
            val list = mutableListOf<Song>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val sObj = jsonArray.getJSONObject(i)
                val id = sObj.optString("videoId", "")
                if (id.isNotBlank()) {
                    val song = Song(
                        videoId = id,
                        title = sObj.optString("title", ""),
                        artist = sObj.optString("artist", ""),
                        thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    list.add(song)
                }
            }
            _favorites.value = list
            val ids = list.mapTo(HashSet()) { it.videoId }
            _favoriteIds.value = ids
            LikeState.seedLiked(ids)
        }
    }

    fun isFavorite(videoId: String): Boolean = videoId in _favoriteIds.value

    fun addFavorite(song: Song) {
        if (song.videoId.isBlank()) return
        val current = _favorites.value
        val without = current.filterNot { it.videoId == song.videoId }
        val updated = listOf(song) + without
        _favorites.value = updated
        _favoriteIds.value = _favoriteIds.value + song.videoId
        LikeState.set(song.videoId, LikeStatus.LIKE)
        persist(updated)
    }

    fun removeFavorite(videoId: String) {
        if (videoId.isBlank()) return
        val updated = _favorites.value.filterNot { it.videoId == videoId }
        _favorites.value = updated
        _favoriteIds.value = _favoriteIds.value - videoId
        LikeState.set(videoId, LikeStatus.INDIFFERENT)
        persist(updated)
    }

    fun toggleFavorite(song: Song): Boolean {
        return if (isFavorite(song.videoId)) {
            removeFavorite(song.videoId)
            false
        } else {
            addFavorite(song)
            true
        }
    }

    fun getFavorites(): List<Song> = _favorites.value

    fun setFavorites(songs: List<Song>) {
        val distinct = songs.distinctBy { it.videoId }
        _favorites.value = distinct
        val ids = distinct.mapTo(HashSet()) { it.videoId }
        _favoriteIds.value = ids
        LikeState.seedLiked(ids)
        persist(distinct)
    }

    fun importFavorites(songs: List<Song>, merge: Boolean = true) {
        val combined = if (merge) {
            val existing = _favorites.value
            (songs + existing).distinctBy { it.videoId }
        } else {
            songs.distinctBy { it.videoId }
        }
        setFavorites(combined)
    }

    private fun persist(list: List<Song>) {
        if (!this::prefs.isInitialized) return
        runCatching {
            val jsonArray = JSONArray()
            for (song in list) {
                val sObj = JSONObject()
                sObj.put("videoId", song.videoId)
                sObj.put("title", song.title)
                sObj.put("artist", song.artist)
                sObj.put("thumbnailUrl", song.thumbnailUrl)
                sObj.put("durationText", song.durationText)
                sObj.put("artistId", song.artistId)
                sObj.put("albumId", song.albumId)
                sObj.put("albumName", song.albumName)
                sObj.put("isVideo", song.isVideo)
                jsonArray.put(sObj)
            }
            prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
        }
    }
}
