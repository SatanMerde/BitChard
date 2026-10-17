package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.abs

/**
 * Hyper-precise syllable and word-by-word synchronized lyrics from NetEase Cloud Music (YRC format).
 *
 * NetEase stores millisecond-level syllable timestamps in YRC, alongside line-synced LRC fallback.
 */
object NeteaseLyrics {

    private const val DURATION_TOLERANCE_SECONDS = 8
    private const val NETEASE_AGENT = "NeteaseMusic/9.0.0 (iPhone; iOS 16.0; Scale/3.00)"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val seconds = (durationMs / 1000).toInt()
        val query = "$title $artist".trim()
        val songId = searchSongId(query, seconds) ?: return@withContext null

        fetchLyrics(songId)
    }

    private fun searchSongId(query: String, seconds: Int): Long? {
        val url = "http://music.163.com/api/search/get/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "5")
            .build()

        val headers = mapOf(
            "User-Agent" to NETEASE_AGENT,
            "Referer" to "http://music.163.com/",
        )
        val body = lyricsGetWithHeaders(url.toString(), headers) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<SearchResponse>(body) }.getOrNull()
        val songs = response?.result?.songs.orEmpty()
        if (songs.isEmpty()) return null

        val match = if (seconds > 0) {
            songs.filter { abs((it.duration / 1000) - seconds) <= DURATION_TOLERANCE_SECONDS }
                .minByOrNull { abs((it.duration / 1000) - seconds) } ?: songs.firstOrNull()
        } else {
            songs.firstOrNull()
        }
        return match?.id
    }

    private fun fetchLyrics(songId: Long): List<LyricLine>? {
        val url = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("lv", "1")
            .addQueryParameter("kv", "1")
            .addQueryParameter("tv", "1")
            .addQueryParameter("rv", "1")
            .addQueryParameter("yv", "1")
            .build()

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0",
            "Referer" to "https://music.163.com",
        )
        val body = lyricsGetWithHeaders(url.toString(), headers) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<LyricResponse>(body) }.getOrNull() ?: return null

        // 1. Try hyper-precise word-by-word/syllable YRC
        val yrc = response.yrc?.lyric?.trim()
        if (!yrc.isNullOrBlank()) {
            val parsedYrc = KaraokeLrc.parse(yrc)
            if (parsedYrc.any { it.isWordSynced }) {
                return parsedYrc
            }
        }

        // 2. Fall back to standard line-synced LRC
        val lrc = response.lrc?.lyric?.trim()
        if (!lrc.isNullOrBlank()) {
            val parsedLrc = LrcLib.parseLrc(lrc)
            if (parsedLrc.isNotEmpty()) {
                return parsedLrc
            }
        }

        return null
    }

    @Serializable
    private data class SearchResponse(val result: SearchResult? = null, val code: Int = 0) {
        @Serializable
        data class SearchResult(val songs: List<Song> = emptyList())

        @Serializable
        data class Song(val id: Long, val name: String = "", val duration: Long = 0)
    }

    @Serializable
    private data class LyricResponse(
        val yrc: LyricData? = null,
        val lrc: LyricData? = null,
        val code: Int = 0,
    ) {
        @Serializable
        data class LyricData(val lyric: String = "")
    }
}
