package ar.luis.canalestv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Averigua el ID "UC..." de un canal a partir de su @handle leyendo la página pública
 * del canal, y lo guarda para no volver a pedirlo. No usa clave de API.
 */
class ChannelResolver(context: Context) {
    private val prefs = context.getSharedPreferences("channel_ids", Context.MODE_PRIVATE)

    suspend fun channelId(channel: Channel): String? {
        channel.channelId?.let { return it }
        prefs.getString(channel.handle, null)?.let { return it }
        val id = withContext(Dispatchers.IO) { fetch(channel.handle) } ?: return null
        prefs.edit().putString(channel.handle, id).apply()
        return id
    }

    private fun fetch(handle: String): String? = try {
        val conn = URL("https://www.youtube.com/@$handle").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept-Language", "es-AR,es;q=0.9")
        conn.setRequestProperty("Cookie", "CONSENT=YES+")
        val html = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        Regex("""<link rel="canonical" href="https://www\.youtube\.com/channel/(UC[\w-]{22})"""")
            .find(html)?.groupValues?.get(1)
            ?: Regex(""""externalId":"(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
    } catch (e: Exception) {
        null
    }
}
