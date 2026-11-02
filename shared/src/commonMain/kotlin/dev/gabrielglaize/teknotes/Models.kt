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
)

/** A soundsystem or an artist heard during a night. */
data class NightEntry(
    val id: String,
    val nightId: String,
    val soundsystemId: String?,
    val artistId: String?,
    /** Name of the soundsystem or artist, as found in the directory. */
    val label: String,
    /** 1 to 5, or null when not rated. */
    val rating: Int?,
    val comment: String,
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
