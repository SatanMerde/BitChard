package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.time.Instant

/**
 * BitChard ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's "latest release" once per launch and compares its tag
 * and build timestamp against the running build.
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded into the app's cache and handed to the system package installer,
 * so the whole round trip stays inside the app without needing manual browser
 * downloads or reinstallations.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
    )

    private const val CACHE_SUBDIR = "updates"

    private const val RELEASES_URL =
        "https://api.github.com/repos/SatanMerde/BitChard/releases"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/SatanMerde/BitChard/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    private val appBuildTime: Long by lazy {
        runCatching {
            val field = BuildConfig::class.java.getField("BUILD_TIME")
            field.getLong(null)
        }.getOrDefault(0L)
    }

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    /**
     * Automatic check performed at startup.
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        checkInternal(manual = false)
    }

    /**
     * Manual check triggered by user in Settings or UI.
     */
    suspend fun checkManual(): UpdateInfo? = withContext(Dispatchers.IO) {
        checkInternal(manual = true)
    }

    private suspend fun checkInternal(manual: Boolean): UpdateInfo? = runCatching {
        val release = fetchLatestRelease() ?: return null
        val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val releaseName = release["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull
            ?: "https://github.com/SatanMerde/BitChard/releases"
        val apkUrl = apkAssetUrl(release)
        val notes = release["body"]?.jsonPrimitive?.contentOrNull
        val publishedAt = release["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()

        // Extract semantic version from tag or release name
        val versionCandidate = when {
            tag.isNotBlank() && !tag.equals("latest", ignoreCase = true) -> tag.removePrefix("v")
            else -> extractVersionFromText(releaseName) ?: extractVersionFromText(notes.orEmpty()) ?: "latest"
        }

        val isSemanticNewer = versionCandidate != "latest" && isNewer(versionCandidate, BuildConfig.VERSION_NAME)
        val publishedMillis = parseIsoMillis(publishedAt)
        // If release tag is 'latest' or versions compare equal, verify if GitHub release was published after current APK build
        val isTimestampNewer = publishedMillis > 0L && appBuildTime > 0L &&
            publishedMillis > (appBuildTime + 60_000L)

        val isUpdateAvailable = isSemanticNewer || isTimestampNewer

        if (isUpdateAvailable) {
            val displayVersion = if (versionCandidate != "latest") versionCandidate else {
                extractVersionFromText(releaseName) ?: releaseName.ifBlank { "latest" }
            }
            val info = UpdateInfo(displayVersion, releaseUrl, apkUrl, notes)
            _available.value = info
            info
        } else {
            if (!manual) {
                // Keep _available as null if up to date
                _available.value = null
            }
            null
        }
    }.getOrNull()

    private fun fetchLatestRelease(): JsonObject? = runCatching {
        // Try latest release endpoint first
        val request = Request.Builder().url(LATEST_RELEASE_URL).build()
        val body = Http.client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
        if (body != null) {
            return (json.parseToJsonElement(body) as? JsonObject)
        }

        // Fallback to releases list
        val listRequest = Request.Builder().url(RELEASES_URL).build()
        val listBody = Http.client.newCall(listRequest).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        } ?: return null

        val list = json.parseToJsonElement(listBody).jsonArray
        list.firstOrNull() as? JsonObject
    }.getOrNull()

    private fun extractVersionFromText(text: String): String? {
        val regex = Regex("""[vV]?(\d+\.\d+(?:\.\d+)?(?:-[a-zA-Z0-9]+)?)""")
        return regex.find(text)?.groupValues?.getOrNull(1)?.removePrefix("v")?.removePrefix("V")
    }

    private fun parseIsoMillis(iso: String): Long = runCatching {
        if (iso.isBlank()) return 0L
        Instant.parse(iso).toEpochMilli()
    }.getOrDefault(0L)

    /**
     * Wipes any APK left over from a previous run. Called once at cold start
     * so a downloaded update is only ever "Install Now" for the session that
     * downloaded it.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * Scans the release assets for the appropriate phone APK.
     * Supports both BitChard-*.apk and app-prod-*.apk naming, preferring universal
     * builds and falling back to matching ABI splits.
     */
    private fun apkAssetUrl(release: JsonObject): String? = runCatching {
        val apks = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val isApk = name.endsWith(".apk", ignoreCase = true)
                val isUploaded = asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
                if (!isApk || !isUploaded) return@mapNotNull null

                // Skip Wear OS and TV builds
                if (name.contains("wear", ignoreCase = true) || name.contains("tv", ignoreCase = true)) {
                    return@mapNotNull null
                }

                // Check that it's our phone app
                val ours = name.startsWith(BuildConfig.UPDATE_APK_PREFIX, ignoreCase = true) ||
                    name.startsWith("BitChard", ignoreCase = true) ||
                    name.startsWith("bitchord", ignoreCase = true) ||
                    name.startsWith("app-prod", ignoreCase = true) ||
                    name.startsWith("app-", ignoreCase = true)
                if (ours) name.removeSuffix(".apk").removeSuffix(".APK") to url else null
            }
            .orEmpty()

        if (apks.isEmpty()) return@runCatching null

        // 1. Universal build (has "universal" in name or has no ABI suffix and no underscore)
        val universal = apks.firstOrNull { (name, _) ->
            name.contains("universal", ignoreCase = true) ||
                ('_' !in name && !Build.SUPPORTED_ABIS.any { abi -> name.contains(abi, ignoreCase = true) })
        }?.second

        if (universal != null) return@runCatching universal

        // 2. Best ABI matching for this device
        for (abi in Build.SUPPORTED_ABIS) {
            val matchingAbi = apks.firstOrNull { (name, _) -> name.contains(abi, ignoreCase = true) }?.second
            if (matchingAbi != null) return@runCatching matchingAbi
        }

        // 3. Fallback to first available phone APK
        apks.firstOrNull()?.second
    }.getOrNull()

    /**
     * Streams the current update's APK into the app cache, reporting progress
     * through [download]. Once downloaded, immediately prompts the system
     * installer for an effortless in-place update.
     */
    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            // Drop anything left over from an earlier attempt.
            dir.listFiles()?.forEach { it.delete() }
            val cleanVersion = info.version.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val target = File(dir, "bitchard-$cleanVersion.apk")

            val request = Request.Builder().url(url).build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            if (downloadCancelled) {
                                _download.value = DownloadState.Idle
                                target.delete()
                                return@withContext
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            total?.let {
                                _download.value =
                                    DownloadState.Downloading((readTotal.toFloat() / it).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            _download.value = DownloadState.Ready(target)
            // Immediately open Android system package installer
            withContext(Dispatchers.Main) {
                installApk(context, target)
            }
        }.onFailure { error ->
            _download.value = if (downloadCancelled) {
                DownloadState.Idle
            } else {
                DownloadState.Failed(error.message ?: "Download failed")
            }
        }
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /**
     * Hands a downloaded APK to the system installer for in-place upgrade.
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "APK not found", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (opened) {
                Toast.makeText(
                    context,
                    R.string.allow_unknown_apps_to_update,
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            // Fallback for custom vendor ROMs
            runCatching {
                @Suppress("DEPRECATION")
                val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /** A version split into its numeric dotted parts and whether it carries a "-suffix" (e.g. "-beta2"). */
    private data class ParsedVersion(val parts: List<Int>, val isPreRelease: Boolean)

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return ParsedVersion(base.split(".").map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /**
     * Numeric, dot-separated comparison — "1.10" outranks "1.9" — with one
     * extra rule: a "-betaN" build is treated as older than a plain release of the same numbers.
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.isPreRelease && !l.isPreRelease
    }
}
