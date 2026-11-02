package dev.gabrielglaize.teknotes

/** A soundsystem of the shared directory. */
data class Soundsystem(
    val id: String,
    val name: String,
    val aliases: List<String>,
    /** Department code of origin (see [Departments]), or null when unknown. */
    val department: String?,
    val styles: List<Style>,
    val links: List<String>,
)

/** An artist (DJ, live act) of the shared directory. */
data class Artist(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val styles: List<Style>,
    val links: List<String>,
)

/** A night out in the user's notebook. Only the department is ever stored, never a precise place. */
data class Night(
    val id: String,
    /** ISO date (yyyy-MM-dd) of the evening the night started. */
    val date: String,
    val department: String?,
    val title: String,
    val comment: String,
    val shared: Boolean,
    val entryCount: Int,
    /** What was heard, as "Rouille (Caisson Noir)". */
    val sets: List<String>,
    /** Average of the ratings given this night, or null if none. */
    val average: Double?,
)

/**
 * A set heard during a night: an artist playing on a soundsystem, or only one of the two
 * when the other isn't known.
 */
data class NightEntry(
    val id: String,
    val nightId: String,
    val soundsystemId: String?,
    val artistId: String?,
    val artistName: String?,
    val soundsystemName: String?,
    /** "Rouille (Caisson Noir)", "Rouille" or "Caisson Noir". */
    val label: String,
    /** 1 to 5, or null when not rated. */
    val rating: Int?,
    val comment: String,
)

/** What can be added to a night: an artist on a soundsystem, or one of the two alone. */
data class SetChoice(
    val artistId: String?,
    val artistName: String?,
    val soundsystemId: String?,
    val soundsystemName: String?,
) {
    val label: String get() = setLabel(artistName, soundsystemName)
}

internal fun setLabel(artist: String?, soundsystem: String?): String = when {
    artist != null && soundsystem != null -> "$artist ($soundsystem)"
    else -> artist ?: soundsystem.orEmpty()
}

/** A soundsystem or an artist of the user's personal directory: what they noted themselves. */
data class MyDirectoryEntry(
    val id: String,
    val kind: DirectoryHit.Kind,
    val name: String,
    /** Number of nights it was heard. */
    val nights: Int,
    /** Average of the ratings given, or null if none. */
    val average: Double?,
    /** ISO date of the last night. */
    val lastDate: String,
)

/** A set the user noted, with the night it belongs to. */
data class MyNote(
    val entry: NightEntry,
    val nightDate: String,
    val nightTitle: String,
)

/** Who plays where, by id, for directory lists. */
data class DirectoryLinks(
    val soundsystemsOfArtist: Map<String, List<String>>,
    val artistsOfSoundsystem: Map<String, List<String>>,
)

/** One line of a directory search result. */
data class DirectoryHit(
    val id: String,
    val kind: Kind,
    val name: String,
    /** The alias that matched, when the match came from an alias. */
    val matchedAlias: String?,
    val score: Int,
) {
    enum class Kind { SOUNDSYSTEM, ARTIST }
}
