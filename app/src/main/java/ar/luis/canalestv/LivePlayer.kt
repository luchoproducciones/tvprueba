package ar.luis.canalestv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Reproductor oficial de YouTube (IFrame Player API) en un WebView.
 *
 * El WebView NO va adentro de la interfaz Compose: va directo en la ventana, debajo de una capa
 * Compose transparente (menú, carteles). En varias teles, si el WebView queda dentro de Compose,
 * el video se decodifica (se oye) pero no se dibuja.
 */
@SuppressLint("SetJavaScriptEnabled")
class PlayerHost(context: Context, private val container: FrameLayout) {

    var onPlaying: () -> Unit = {}
    var onError: (Int) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private var customView: View? = null

    val webView: WebView = WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(android.graphics.Color.BLACK)
        // El WebView no toma el foco: las teclas del control las maneja la app.
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        webViewClient = WebViewClient()
        webChromeClient = object : WebChromeClient() {
            // Sin esto algunas teles muestran un ícono gris de "play" encima del video.
            override fun getDefaultVideoPoster(): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            // Si el reproductor pasa a pantalla completa, el video llega en una vista aparte.
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                hideCustomView()
                customView = view
                container.addView(view, 1, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            }

            override fun onHideCustomView() = hideCustomView()
        }
        addJavascriptInterface(Bridge(), "Android")
    }

    init {
        container.addView(webView, 0)
    }

    /** Versión del motor web de la tele (para diagnosticar). */
    val engineVersion: String
        get() = if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage()?.versionName ?: "?" else "?"

    fun load(channelId: String, videoId: String?) {
        hideCustomView()
        // YouTube exige que el reproductor embebido se identifique con un origen propio de la app
        // (si no, da "error 152/153"). Usamos https://<paquete de la app>.
        webView.loadDataWithBaseURL(APP_ORIGIN, playerHtml(channelId, videoId), "text/html", "utf-8", null)
    }

    fun stop() {
        hideCustomView()
        webView.loadUrl("about:blank")
    }

    private fun hideCustomView() {
        customView?.let { container.removeView(it) }
        customView = null
    }

    private inner class Bridge {
        @JavascriptInterface
        fun error(code: Int) { main.post { onError(code) } }

        @JavascriptInterface
        fun playing() { main.post { onPlaying() } }
    }
}

private const val APP_ORIGIN = "https://ar.luis.canalestv"

private fun playerHtml(channelId: String, videoId: String?): String {
    // Con videoId reproduce esa transmisión; si no, "live_stream" + channel = el vivo actual del canal.
    val video = videoId ?: "live_stream"
    val channelVar = if (videoId == null) "channel: '$channelId', " else ""
    return """
<!doctype html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
function onYouTubeIframeAPIReady() {
  new YT.Player('p', {
    width: '100%', height: '100%', videoId: '$video',
    playerVars: { ${channelVar}autoplay: 1, controls: 0, rel: 0, playsinline: 1,
                  iv_load_policy: 3, origin: '$APP_ORIGIN', widget_referrer: '$APP_ORIGIN' },
    events: {
      onReady: function (e) { e.target.unMute(); e.target.playVideo(); },
      onStateChange: function (e) { if (e.data === 1) Android.playing(); },
      onError: function (e) { Android.error(e.data); }
    }
  });
}
</script></body></html>
"""
}
