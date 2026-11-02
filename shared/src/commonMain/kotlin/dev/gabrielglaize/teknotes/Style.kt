package dev.gabrielglaize.teknotes

/**
 * A music style. The list is open: [common] is only what the app suggests, anyone can add another.
 * Known styles are stored by stable id, the others by their name as written.
 */
data class Style(val id: String, val label: String) {
    companion object {
        val TEKNO = Style("tekno", "Tekno")
        val TRIBE = Style("tribe", "Tribe")
        val HARDTEK = Style("hardtek", "Hardtek")
        val ACID = Style("acid", "Acid")
        val MENTAL = Style("mental", "Mental")
        val FRENCHCORE = Style("frenchcore", "Frenchcore")
        val HARDCORE = Style("hardcore", "Hardcore")
        val SPEEDCORE = Style("speedcore", "Speedcore")
        val RAGGATEK = Style("raggatek", "Raggatek")
        val TECHNO = Style("techno", "Techno")
        val MINIMAL = Style("minimal", "Minimal")
        val PSYTRANCE = Style("psytrance", "Psytrance")
        val DRUM_AND_BASS = Style("dnb", "Drum & bass")
        val DUB = Style("dub", "Dub")

        val common: List<Style> = listOf(
            TEKNO, TRIBE, HARDTEK, ACID, MENTAL, FRENCHCORE, HARDCORE, SPEEDCORE, RAGGATEK, TECHNO, MINIMAL,
            PSYTRANCE, DRUM_AND_BASS, DUB,
        )

        const val MAX_LENGTH = 32

        fun fromId(id: String): Style = common.firstOrNull { it.id == id } ?: Style(id, id)

        /** The style typed by someone: a known one if it only differs by case or accents, else a new one. */
        fun fromLabel(text: String): Style? {
            val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH)
            if (Search.normalize(clean).isEmpty()) return null
            val key = Search.normalize(clean)
            return common.firstOrNull { Search.normalize(it.label) == key || it.id == key }
                ?: clean.replaceFirstChar { it.uppercase() }.let { Style(it, it) }
        }
    }
}
