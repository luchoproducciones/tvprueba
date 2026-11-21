package ar.luis.listasm3u

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Reproductor de video (Media3 / ExoPlayer). Reproduce HLS (.m3u8), DASH (.mpd) y
 * transmisiones directas (.ts, .mp4...). No reproduce contenido protegido con DRM.
 *
 * La vista del video va directo en la ventana, debajo de la interfaz Compose transparente.
 */
@OptIn(UnstableApi::class)
class TvPlayer(private val context: Context, container: FrameLayout) {

    var onPlaying: () -> Unit = {}
    var onBuffering: () -> Unit = {}
    var onError: (String) -> Unit = {}

    private val exo: ExoPlayer = ExoPlayer.Builder(context).build()
    private var current: M3uChannel? = null
    private var triedHls = false

    private val view = PlayerView(context).apply {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        useController = false
        player = exo
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(android.graphics.Color.BLACK)
        setKeepContentOnPlayerReset(false)
        isFocusable = false
        keepScreenOn = true
    }

    init {
        container.addView(view, 0)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> onPlaying()
                    Player.STATE_BUFFERING -> onBuffering()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val ch = current ?: return
                when {
                    // Una transmisión en vivo que se quedó atrás: volver al momento actual.
                    error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        exo.seekToDefaultPosition(); exo.prepare()
                    }
                    // Muchos enlaces no dicen qué formato son: si falló, probar como HLS.
                    !triedHls && guessMime(ch.url) == null -> {
                        triedHls = true
                        start(ch, MimeTypes.APPLICATION_M3U8)
                    }
                    else -> onError(describe(error))
                }
            }
        })
    }

    fun play(ch: M3uChannel) {
        triedHls = false
        start(ch, guessMime(ch.url))
    }

    private fun start(ch: M3uChannel, mime: String?) {
        current = ch
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent ?: PlaylistStore.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ch.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
        val item = MediaItem.Builder().setUri(ch.url).apply { if (mime != null) setMimeType(mime) }.build()
        exo.setMediaSource(sources.createMediaSource(item))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun stop() {
        current = null
        exo.stop()
        exo.clearMediaItems()
    }

    fun pause() { exo.playWhenReady = false }
    fun resume() { if (current != null) { exo.playWhenReady = true; if (exo.playbackState == Player.STATE_IDLE) exo.prepare() } }
    fun release() = exo.release()

    /** Datos técnicos del video (resolución), para mostrar en la barra de info. */
    val quality: String?
        get() = exo.videoFormat?.let { if (it.height > 0) "${it.height}p" else null }

    private fun guessMime(url: String): String? {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> null
        }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "No hay conexión con el canal."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "El canal no responde (enlace caído o vencido)."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
            "Formato de video no compatible."
        in PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_UNSPECIFIED + 999 ->
            "Este canal está protegido y no se puede reproducir."
        in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "La tele no puede decodificar este video."
        else -> "No se pudo reproducir (${e.errorCodeName})."
    }
}
