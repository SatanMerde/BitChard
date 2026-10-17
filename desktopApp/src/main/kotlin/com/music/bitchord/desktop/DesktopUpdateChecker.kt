package com.music.bitchord.desktop

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Looks at the repo's "latest release" on GitHub once per launch and says whether it is newer
 * than the running build. The installer is not run from here: the dialog opens the asset in the
 * browser, so the user installs it the way they installed the first one.
 *
 * GitHub's "latest" skips pre-releases and drafts, so a `-beta` desktop build is only ever
 * nudged toward a published release.
 */
internal object DesktopUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        /** The installer for this platform, or null when the release has none. */
        val downloadUrl: String?,
        val notes: String?,
    )

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/SatanMerde/BitChard/releases/latest"
    private const val RELEASES_URL =
        "https://api.github.com/repos/SatanMerde/BitChard/releases"

    val currentVersion: String = System.getProperty("bitchard.version") ?: System.getProperty("bitchord.version") ?: "1.8.4"

    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun check(): UpdateInfo? = runCatching {
        val release = fetchRelease() ?: return@runCatching null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        val latest = tag.removePrefix("v")
        if (!isNewer(latest, currentVersion)) return@runCatching null
        UpdateInfo(
            version = latest,
            releaseUrl = url,
            downloadUrl = installerUrl(release),
            notes = release["body"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()

    private suspend fun fetchRelease(): JsonObject? = runCatching {
        val userAgent = "BitChard/$currentVersion (Desktop)"

        // Try latest release first
        val latestResp = http.get(LATEST_RELEASE_URL) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", userAgent)
        }
        if (latestResp.status.isSuccess()) {
            val obj = json.parseToJsonElement(latestResp.bodyAsText()) as? JsonObject
            if (obj != null && installerUrl(obj) != null) return obj
        }

        // Fallback to releases list
        val listResp = http.get(RELEASES_URL) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", userAgent)
        }
        if (!listResp.status.isSuccess()) return null
        val list = json.parseToJsonElement(listResp.bodyAsText()).jsonArray
        for (item in list) {
            val obj = item as? JsonObject ?: continue
            val isDraft = obj["draft"]?.jsonPrimitive?.contentOrNull == "true"
            if (!isDraft && installerUrl(obj) != null) return obj
        }
        null
    }.getOrNull()

    /**
     * Matches desktop installer assets for the current platform.
     */
    private fun installerUrl(release: JsonObject): String? {
        val suffixes = when {
            DesktopPlatform.isWindows -> listOf("-windows-x64-setup.exe", ".exe", ".msi", "-windows-portable.zip")
            DesktopPlatform.isLinux -> listOf(".AppImage", "-linux-amd64.deb", ".deb", ".tar.gz")
            DesktopPlatform.isMac -> listOf(".dmg", ".pkg")
            else -> return null
        }
        val assets = release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        for (suffix in suffixes) {
            val hit = assets.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(suffix, ignoreCase = true) == true &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            hit?.get("browser_download_url")?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        return null
    }

    private class Parsed(val parts: List<Int>, val preRelease: Boolean)

    private fun parse(raw: String): Parsed {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return Parsed(base.split('.').map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Numeric comparison, with a `-betaN` build counted as older than the plain release it leads up to. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.preRelease && !l.preRelease
    }
}
