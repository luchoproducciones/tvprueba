package ar.luis.canalestv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Canales que el usuario agrega desde la app ("Mis canales"), guardados en el equipo. */
class ChannelStore(context: Context) {
    private val prefs = context.getSharedPreferences("mis_canales", Context.MODE_PRIVATE)

    fun load(): List<Channel> {
        val arr = JSONArray(prefs.getString("lista", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Channel(
                name = o.getString("name"),
                handle = o.optString("handle"),
                category = CATEGORY,
                colorHex = o.getLong("color"),
                channelId = o.optString("channelId").ifBlank { null },
                videoId = o.optString("videoId").ifBlank { null },
                custom = true,
            )
        }
    }

    fun save(list: List<Channel>) {
        val arr = JSONArray()
        list.filter { it.custom }.forEach {
            arr.put(JSONObject().put("name", it.name).put("handle", it.handle)
                .put("color", it.colorHex).put("channelId", it.channelId ?: "").put("videoId", it.videoId ?: ""))
        }
        prefs.edit().putString("lista", arr.toString()).apply()
    }

    companion object {
        const val CATEGORY = "Mis canales"
        private val COLORS = listOf(0xFF2E7D32, 0xFF5D4037, 0xFFAD1457, 0xFF00796B, 0xFF512DA8, 0xFF455A64)

        /**
         * Convierte lo que pegue el usuario en un canal. Acepta:
         * youtube.com/@usuario, @usuario, youtube.com/channel/UC..., o un enlace a un vivo
         * de esos canales (youtube.com/@usuario/live), o el link a una transmisión puntual
         * (youtube.com/watch?v=..., youtu.be/..., youtube.com/live/...).
         */
        fun parse(input: String, name: String, index: Int): Channel? {
            val text = input.trim()
            val video = Regex("""(?:[?&]v=|youtu\.be/|/live/)([\w-]{11})""").find(text)?.groupValues?.get(1)
            if (video != null) {
                val title = name.trim().ifBlank { "Canal ${index + 1}" }
                return Channel(title, "", CATEGORY, COLORS[index % COLORS.size], videoId = video, custom = true)
            }
            val handle = Regex("""@([\w.\-]{3,30})""").find(text)?.groupValues?.get(1)
            val id = Regex("""(UC[\w-]{22})""").find(text)?.groupValues?.get(1)
            if (handle == null && id == null) return null
            val title = name.trim().ifBlank { handle ?: "Canal ${index + 1}" }
            return Channel(title, handle ?: "", CATEGORY, COLORS[index % COLORS.size], id, custom = true)
        }
    }
}
