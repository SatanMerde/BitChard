package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val APPLE_MUSIC_LOGIN_URL = "https://music.apple.com/login"

private val APPLE_MUSIC_COOKIE_URLS = listOf(
    "https://music.apple.com",
    "https://apple.com",
    "https://idmsa.apple.com",
)

private val APPLE_MUSIC_LOGIN_HOST_SUFFIXES = listOf(
    "apple.com", "apple-music.com", "icloud.com",
)

private fun hostMatches(uri: Uri?, suffixes: List<String>): Boolean {
    if (uri?.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return suffixes.any { host == it || host.endsWith(".$it") }
}

fun clearAppleMusicWebSession() {
    val cookies = CookieManager.getInstance()
    for (url in APPLE_MUSIC_COOKIE_URLS) {
        val names = cookies.getCookie(url)?.split(";")
            ?.map { it.substringBefore("=").trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        for (name in names) {
            val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            cookies.setCookie(url, expired)
            cookies.setCookie(url, "$expired; Domain=.apple.com")
        }
    }
    cookies.flush()
}

/**
 * In-app Apple Music sign-in WebView.
 * Captures Apple Music's `media-user-token` (or session cookie) to fetch playlists & library items.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppleMusicLoginScreen(
    onConnected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = browserUserAgent(settings.userAgentString)
                setBackgroundColor(0xFF121212.toInt())
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                var sent = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = !hostMatches(request.url, APPLE_MUSIC_LOGIN_HOST_SUFFIXES)

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
                        if (sent) return
                        val raw = CookieManager.getInstance().getCookie("https://music.apple.com") ?: return
                        val cookiesList = raw.split(";").map { it.trim() }
                        // Media user token or session authorization cookie
                        val userToken = cookiesList
                            .firstOrNull { it.startsWith("media-user-token=") || it.startsWith("music.apple.com.media-user-token=") }
                            ?.substringAfter("=")
                            ?: cookiesList.firstOrNull { it.startsWith("session-id=") }?.substringAfter("=")
                            ?: if (url != null && !url.contains("/login") && !url.contains("/auth") && (raw.contains("myacinfo") || raw.contains("itspod"))) raw else null

                        if (!userToken.isNullOrBlank()) {
                            sent = true
                            view.visibility = View.GONE
                            onConnected(userToken)
                        }
                    }
                }
                webView = this
                loadUrl(APPLE_MUSIC_LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
