package dev.gabrielglaize.teknotes

/**
 * Typo- and accent-tolerant search, run locally so it also works offline.
 * "teknokrat", "Tekno Krates" and "TEKNOKRATES" all find "Teknokrates".
 */
object Search {
    private val accents = mapOf(
        'à' to "a", 'â' to "a", 'ä' to "a", 'á' to "a", 'ã' to "a", 'å' to "a",
        'ç' to "c", 'é' to "e", 'è' to "e", 'ê' to "e", 'ë' to "e",
        'î' to "i", 'ï' to "i", 'í' to "i", 'ì' to "i", 'ñ' to "n",
        'ô' to "o", 'ö' to "o", 'ó' to "o", 'ò' to "o", 'õ' to "o",
        'ù' to "u", 'û' to "u", 'ü' to "u", 'ú' to "u", 'ÿ' to "y", 'ý' to "y",
        'œ' to "oe", 'æ' to "ae", 'ß' to "ss",
    )

    /** Lower case, no accents, letters and digits only: "Tekno Krates !" -> "teknokrates". */
    fun normalize(text: String): String = buildString {
        for (char in text.lowercase()) {
            val plain = accents[char]
            when {
                plain != null -> append(plain)
                char.isLetterOrDigit() -> append(char)
            }
        }
    }

    /** Edit distance between two strings (insertions, deletions, substitutions). */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Score of [candidate] for [query], higher is better, or null when it doesn't match.
     * Exact > starts with > contains > close enough (a typo every 4 letters).
     */
    fun score(query: String, candidate: String): Int? {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return null
        return when {
            c == q -> 100
            c.startsWith(q) -> 80
            c.contains(q) -> 60
            else -> {
                // Compare with the start of the candidate, so a short query can match a long name.
                val prefix = c.take(q.length + 1)
                val typos = minOf(distance(q, c), distance(q, prefix), distance(q, c.take(q.length)))
                val allowed = maxOf(1, q.length / 4)
                if (q.length >= 3 && typos <= allowed) 40 - typos * 5 else null
            }
        }
    }

    /** Best match of [query] among a name and its aliases. */
    fun bestMatch(query: String, name: String, aliases: List<String>): Pair<Int, String?>? {
        val onName = score(query, name)?.let { it + 1 to null } // the name wins over an equal alias
        val onAlias = aliases.mapNotNull { alias -> score(query, alias)?.let { it to alias } }.maxByOrNull { it.first }
        return listOfNotNull(onName, onAlias).maxByOrNull { it.first }
    }

    fun soundsystems(query: String, items: List<Soundsystem>): List<DirectoryHit> =
        items.mapNotNull { item ->
            bestMatch(query, item.name, item.aliases)?.let { (score, alias) ->
                DirectoryHit(item.id, DirectoryHit.Kind.SOUNDSYSTEM, item.name, alias, score)
            }
        }

    fun artists(query: String, items: List<Artist>): List<DirectoryHit> =
        items.mapNotNull { item ->
            bestMatch(query, item.name, item.aliases)?.let { (score, alias) ->
                DirectoryHit(item.id, DirectoryHit.Kind.ARTIST, item.name, alias, score)
            }
        }

    fun rank(hits: List<DirectoryHit>): List<DirectoryHit> =
        hits.sortedWith(compareByDescending<DirectoryHit> { it.score }.thenBy { it.name.lowercase() })
}
