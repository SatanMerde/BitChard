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

private const val DEEZER_LOGIN_URL = "https://www.deezer.com/login"

private val DEEZER_COOKIE_URLS = listOf(
    "https://www.deezer.com",
    "https://deezer.com",
)

private val DEEZER_LOGIN_HOST_SUFFIXES = listOf(
    "deezer.com", "google.com", "gstatic.com", "facebook.com", "apple.com",
)

private fun hostMatches(uri: Uri?, suffixes: List<String>): Boolean {
    if (uri?.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return suffixes.any { host == it || host.endsWith(".$it") }
}

fun clearDeezerWebSession() {
    val cookies = CookieManager.getInstance()
    for (url in DEEZER_COOKIE_URLS) {
        val names = cookies.getCookie(url)?.split(";")
            ?.map { it.substringBefore("=").trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        for (name in names) {
            val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            cookies.setCookie(url, expired)
            cookies.setCookie(url, "$expired; Domain=.deezer.com")
        }
    }
    cookies.flush()
}

/**
 * In-app Deezer sign-in WebView.
 * Captures Deezer's `arl` cookie upon successful login to grant access to the user's library.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(
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
                    ): Boolean = !hostMatches(request.url, DEEZER_LOGIN_HOST_SUFFIXES)

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
                        if (sent) return
                        val raw = CookieManager.getInstance().getCookie("https://www.deezer.com") ?: return
                        val arlToken = raw.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("arl=") }
                            ?.substringAfter("arl=")
                            ?.takeIf { it.isNotBlank() }
                            ?: return

                        sent = true
                        view.visibility = View.GONE
                        onConnected(arlToken)
                    }
                }
                webView = this
                loadUrl(DEEZER_LOGIN_URL)
            }
        },
        onRelease = { it.destroy() },
    )

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
