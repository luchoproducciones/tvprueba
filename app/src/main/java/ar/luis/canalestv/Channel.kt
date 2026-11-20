package ar.luis.canalestv

import android.net.Uri

/**
 * Un canal de YouTube que transmite en vivo.
 * [handle] es el @usuario del canal en YouTube (sin la @).
 * [channelId] es el ID "UC..." del canal. Si se deja vacío, la app lo averigua sola
 * a partir del @handle la primera vez y lo guarda (ver ChannelResolver).
 * El reproductor usa ese ID para mostrar "el vivo actual del canal", sin clave de API.
 */
data class Channel(
    val name: String,
    val handle: String,
    val category: String,
    val colorHex: Long,
    val channelId: String? = null,
    /**
     * ID de un video/transmisión puntual (lo que va después de watch?v=). Si está, se reproduce
     * ese video en vez de "el vivo actual del canal". Sirve para transmisiones 24 h, pero si el canal
     * corta y reinicia el vivo, el link deja de andar: en ese caso conviene usar el @handle.
     */
    val videoId: String? = null,
    /** true si lo agregó el usuario desde la app (se puede borrar). */
    val custom: Boolean = false,
    /** Texto corto opcional para el cuadradito de color (si no, se arma con el nombre). */
    val label: String? = null,
) {
    /** Texto corto para el cuadradito de color del menú lateral. */
    val short: String get() = label ?: if (name.length <= 4) name else name.take(4).uppercase()

    val liveUri: Uri
        get() = if (videoId != null) Uri.parse("https://www.youtube.com/watch?v=$videoId")
        else if (handle.isNotBlank()) Uri.parse("https://www.youtube.com/@$handle/live")
        else Uri.parse("https://www.youtube.com/channel/$channelId/live")
}

/**
 * Lista inicial. Los @handles hay que verificarlos abriendo youtube.com/@handle en un navegador;
 * si alguno cambió, se corrige acá.
 */
val CHANNELS = listOf(
    // Noticias
    Channel("TN", "todonoticias", "Noticias", 0xFF0D47A1),
    Channel("LN+", "LNmas", "Noticias", 0xFF1565C0),
    Channel("C5N", "c5n", "Noticias", 0xFFB71C1C),
    Channel("A24", "A24com", "Noticias", 0xFFE65100),
    Channel("Canal 26", "canal26", "Noticias", 0xFF283593, label = "26"),
    Channel("Crónica TV", "cronicatv", "Noticias", 0xFFD32F2F, label = "CR"),
    Channel("TV Pública", "TVPublicaArgentina", "Noticias", 0xFF00838F, label = "TVP"),
    Channel("IP Noticias", "IPNoticias", "Noticias", 0xFF4527A0, label = "IP"),
    // Streaming
    Channel("Olga", "olgaenvivo", "Streaming", 0xFF6A1B9A),
    Channel("Luzu TV", "LuzuTV", "Streaming", 0xFF00695C),
    Channel("Neura", "NeuraMedia", "Streaming", 0xFF37474F),
    // Otros (agregados por link a una transmisión)
    Channel("Latina TV", "", "Otros", 0xFF2E7D32, videoId = "sLzfveqNEJc", label = "LT"),
    Channel("Caso Cerrado", "", "Otros", 0xFFAD1457, videoId = "CG7UYN5OrQA", label = "CC"),
    Channel("Canal 7", "", "Otros", 0xFF0277BD, videoId = "bNel_SWCQgU", label = "7"),
    Channel("Canal 7 Mendoza", "", "Otros", 0xFF00897B, videoId = "Vh8xmLBJtR8", label = "7 MZA"),
    Channel("TVA", "", "Otros", 0xFF6D4C41, videoId = "tAzdtbBzDds"),
    Channel("Telefe Santa Fe", "", "Otros", 0xFF1E88E5, videoId = "RQIXbYD_Vkg", label = "TF SF"),
    Channel("Radio Mitre", "", "Otros", 0xFFC62828, videoId = "4kHIIA_Dp3Y", label = "MITRE"),
    Channel("Canal nuevo", "", "Otros", 0xFF546E7A, videoId = "vGNglKWqwcQ", label = "NUEVO"),
    Channel("Mega", "", "Otros", 0xFFE53935, videoId = "xG4dV3c4OWs"),
    Channel("El Tres TV", "", "Otros", 0xFF3949AB, videoId = "U53-TMYUSik", label = "3"),
)
