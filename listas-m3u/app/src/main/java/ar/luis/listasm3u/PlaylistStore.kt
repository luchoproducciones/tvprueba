package ar.luis.listasm3u

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una lista guardada. Si tiene [url] se vuelve a bajar al abrirla; si no, viene de un archivo. */
data class Playlist(val id: String, val name: String, val url: String?)

/**
 * Guarda en la tele las listas del usuario, sus favoritos y el último canal visto.
 * El contenido de cada lista se copia en un archivo interno, así funciona aunque
 * después se borre el archivo original o el servidor no responda un rato.
 */
class PlaylistStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("listas", Context.MODE_PRIVATE)

    fun lists(): List<Playlist> {
        val arr = JSONArray(prefs.getString("lists", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Playlist(o.getString("id"), o.getString("name"), o.optString("url").ifBlank { null })
        }
    }

    private fun saveLists(lists: List<Playlist>) {
        val arr = JSONArray()
        lists.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url ?: "")) }
        prefs.edit().putString("lists", arr.toString()).apply()
    }

    private fun cacheFile(id: String) = File(context.filesDir, "lista_$id.m3u")

    /** Agrega una lista por URL: la baja y la revisa antes de guardarla. */
    suspend fun addFromUrl(name: String, url: String): Result<Playlist> = runCatching {
        val clean = url.trim().let { if ("://" in it) it else "http://$it" }
        val text = download(clean)
        check(M3uParser.parse(text).isNotEmpty()) { "La dirección no tiene canales M3U." }
        val pl = Playlist(newId(), name.trim().ifBlank { clean.substringAfter("://").substringBefore('/') }, clean)
        withContext(Dispatchers.IO) { cacheFile(pl.id).writeText(text) }
        saveLists(lists() + pl)
        pl
    }

    /** Agrega una lista desde un archivo elegido en el explorador de archivos. */
    suspend fun addFromFile(uri: Uri): Result<Playlist> = runCatching {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } ?: error("No se pudo abrir el archivo.")
        check(M3uParser.parse(text).isNotEmpty()) { "El archivo no tiene canales M3U." }
        val pl = Playlist(newId(), fileName(uri)?.substringBeforeLast('.') ?: "Lista", null)
        withContext(Dispatchers.IO) { cacheFile(pl.id).writeText(text) }
        saveLists(lists() + pl)
        pl
    }

    fun remove(pl: Playlist) {
        saveLists(lists().filter { it.id != pl.id })
        cacheFile(pl.id).delete()
        if (prefs.getString("lastList", null) == pl.id) prefs.edit().remove("lastList").remove("lastUrl").apply()
    }

    /**
     * Abre una lista. Si es por URL intenta bajar la versión nueva;
     * si no hay conexión usa la última copia guardada.
     */
    suspend fun load(pl: Playlist): Result<List<M3uChannel>> = runCatching {
        val file = cacheFile(pl.id)
        val text = if (pl.url != null) {
            try {
                download(pl.url).also { withContext(Dispatchers.IO) { file.writeText(it) } }
            } catch (e: Exception) {
                if (file.exists()) withContext(Dispatchers.IO) { file.readText() } else throw e
            }
        } else withContext(Dispatchers.IO) { file.readText() }
        M3uParser.parse(text).also { check(it.isNotEmpty()) { "La lista está vacía." } }
    }

    // --- Favoritos y último canal ---

    fun favorites(): Set<String> = prefs.getStringSet("favs", emptySet())!!.toSet()

    fun toggleFavorite(url: String): Set<String> {
        val favs = favorites().toMutableSet()
        if (!favs.remove(url)) favs.add(url)
        prefs.edit().putStringSet("favs", favs).apply()
        return favs
    }

    var lastListId: String?
        get() = prefs.getString("lastList", null)
        set(v) = prefs.edit().putString("lastList", v).apply()

    var lastChannelUrl: String?
        get() = prefs.getString("lastUrl", null)
        set(v) = prefs.edit().putString("lastUrl", v).apply()

    // --- Ayudantes ---

    private fun newId() = System.currentTimeMillis().toString(36)

    private fun fileName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private suspend fun download(address: String): String = withContext(Dispatchers.IO) {
        var url = URL(address)
        // Sigue redirecciones a mano (Java no sigue saltos de http a https).
        repeat(6) {
            val con = url.openConnection() as HttpURLConnection
            con.instanceFollowRedirects = false
            con.connectTimeout = 15_000
            con.readTimeout = 30_000
            con.setRequestProperty("User-Agent", USER_AGENT)
            try {
                when (val code = con.responseCode) {
                    in 200..299 -> return@withContext con.inputStream.use { it.readBytes().decodeToString() }
                    301, 302, 303, 307, 308 -> {
                        val next = con.getHeaderField("Location") ?: error("Redirección sin destino")
                        url = URL(url, next)
                    }
                    else -> error("El servidor respondió con error $code.")
                }
            } finally {
                con.disconnect()
            }
        }
        error("Demasiadas redirecciones.")
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 11; Android TV) ListasM3U/0.1"
    }
}
