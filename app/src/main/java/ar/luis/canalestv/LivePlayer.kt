package ar.luis.canalestv

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Reproductor oficial de YouTube (IFrame Player API) dentro de un WebView.
 * Muestra el vivo actual del canal con videoId "live_stream" + parámetro "channel".
 * Publicidad y restricciones las sigue manejando YouTube.
 *
 * [onError] recibe el código de error de YouTube (101 y 150 = el canal no permite
 * ver sus videos fuera de YouTube).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePlayer(channelId: String, videoId: String?, modifier: Modifier = Modifier, onError: (Int) -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(0xFF000000.toInt())
            // El WebView no toma el foco: las teclas del control las maneja la app.
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
    }
    val bridge = remember { PlayerBridge() }
    bridge.onError = onError

    LaunchedEffect(channelId, videoId) {
        webView.removeJavascriptInterface("Android")
        webView.addJavascriptInterface(bridge, "Android")
        webView.loadDataWithBaseURL("https://www.youtube.com", playerHtml(channelId, videoId), "text/html", "utf-8", null)
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private class PlayerBridge {
    var onError: (Int) -> Unit = {}
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun error(code: Int) { main.post { onError(code) } }
}

private fun playerHtml(channelId: String, videoId: String?): String {
    // Con videoId reproduce esa transmisión; si no, "live_stream" + channel = el vivo actual del canal.
    val video = videoId ?: "live_stream"
    val channelVar = if (videoId == null) "channel: '$channelId', " else ""
    return """
<!doctype html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
function onYouTubeIframeAPIReady() {
  new YT.Player('p', {
    width: '100%', height: '100%', videoId: '$video',
    playerVars: { ${channelVar}autoplay: 1, controls: 0, rel: 0, playsinline: 1,
                  iv_load_policy: 3, origin: 'https://www.youtube.com' },
    events: {
      onReady: function (e) { e.target.unMute(); e.target.playVideo(); },
      onError: function (e) { Android.error(e.data); }
    }
  });
}
</script></body></html>
"""
}
