package ar.luis.listasm3u

/** Un canal leído de una lista M3U. */
data class M3uChannel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val userAgent: String?,
    val referrer: String?,
)

/**
 * Lee listas M3U / M3U8 "extendidas":
 *   #EXTINF:-1 tvg-logo="..." group-title="Noticias",Nombre del canal
 *   http://servidor/canal.m3u8
 * También entiende #EXTGRP, #EXTVLCOPT (user-agent / referrer) y el formato
 * "url|User-Agent=...&Referer=..." que usan algunas listas.
 */
object M3uParser {
    private val attr = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    const val NO_GROUP = "Sin grupo"

    fun parse(text: String): List<M3uChannel> {
        val out = ArrayList<M3uChannel>()
        var name: String? = null
        var group: String? = null
        var extGroup: String? = null
        var logo: String? = null
        var ua: String? = null
        var ref: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val comma = nameComma(line)
                    val head = if (comma >= 0) line.substring(0, comma) else line
                    val attrs = attr.findAll(head).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = (if (comma >= 0) line.substring(comma + 1).trim() else "")
                        .ifBlank { attrs["tvg-name"].orEmpty() }
                    group = attrs["group-title"]
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    attrs["user-agent"]?.let { ua = it }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':').trim()
                    val key = opt.substringBefore('=').lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> ua = value
                        "http-referrer", "http-referer" -> ref = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    var url = line
                    if ('|' in line) {
                        url = line.substringBefore('|')
                        for (pair in line.substringAfter('|').split('&')) {
                            val k = pair.substringBefore('=').trim().lowercase()
                            val v = pair.substringAfter('=', "").trim()
                            if (k == "user-agent") ua = v
                            if (k == "referer" || k == "referrer") ref = v
                        }
                    }
                    if (url.contains("://")) {
                        out += M3uChannel(
                            name = name?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/').ifBlank { url },
                            url = url,
                            group = (group ?: extGroup)?.takeIf { it.isNotBlank() } ?: NO_GROUP,
                            logo = logo,
                            userAgent = ua?.takeIf { it.isNotBlank() },
                            referrer = ref?.takeIf { it.isNotBlank() },
                        )
                    }
                    name = null; group = null; logo = null; ua = null; ref = null
                    // #EXTGRP vale para los canales que siguen hasta el próximo #EXTGRP.
                }
            }
        }
        return out
    }

    /** La coma que separa los datos del nombre: la primera que no está entre comillas. */
    private fun nameComma(line: String): Int {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }
}
