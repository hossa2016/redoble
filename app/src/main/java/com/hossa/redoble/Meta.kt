package com.hossa.redoble

/**
 * Limpia lo que mandan las apps de música, sobre todo YouTube y radios:
 *  «Queens of the Stone Age - Go with the Flow (Official Video)» con artista «LyricsTime»
 *  queda como canción «Go with the Flow» y artista «Queens of the Stone Age».
 */
object Meta {
    private val CHANNEL = Regex("(?i)(lyrics?|letras?|vevo|records|official|oficial|karaoke|\\bradio\\b|\\btv\\b|channel|canal|music\\s*hub|\\bmix\\b)")
    private val NOISE = Regex("(?i)\\s*[\\(\\[][^\\)\\]]*(official|oficial|video|v[ií]deo|lyrics?|letra|audio|visuali[sz]er|hd|4k|remaster(ed)?)[^\\)\\]]*[\\)\\]]")
    private val TAIL = Regex("(?i)\\s*(♫|♪|\\||//)?\\s*(lyrics?|letra|con letra|official (music )?video|video oficial)\\s*$")
    private val SEP = Regex("\\s+[-–—|]\\s+|\\s+\\.\\s+|\\s*♫\\s*")

    /** ¿Parece un canal o una emisora y no un artista? («LyricsTime», «Radio en vivo»…) */
    fun channelLike(a: String) = a.isBlank() || CHANNEL.containsMatchIn(a) || a.endsWith(" - Topic")

    private fun tidy(t: String) = t.replace(NOISE, "").replace(TAIL, "").trim().trim('-', '–', '|', '.', ' ')

    /** (canción, artista) ya limpios. */
    fun split(title0: String, artist0: String): Pair<String, String> {
        var title = title0.trim(); var artist = artist0.removeSuffix(" - Topic").trim()
        val parts = SEP.split(title, 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            val left = parts[0].trim(); val right = parts[1].trim()
            when {
                channelLike(artist) -> { artist = left; title = right }
                left.equals(artist, true) -> title = right
            }
        }
        if (channelLike(artist)) artist = ""
        return tidy(title).ifBlank { title0.trim() } to artist
    }

    /** Un artista ya guardado en el historial, arreglado (o null si no es un artista). */
    fun artistKey(a: String): String? {
        val left = SEP.split(a.trim(), 2)[0].trim()
        return left.takeIf { it.length in 2..60 && !channelLike(it) }
    }
}
