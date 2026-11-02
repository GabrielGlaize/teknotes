package dev.gabrielglaize.teknotes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.gabrielglaize.teknotes.db.TeknotesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The shared directory of soundsystems and artists, as cached on the phone. */
class DirectoryRepository internal constructor(
    private val queries: TeknotesQueries,
    private val clock: () -> Instant,
) {
    private val soundsystemMapper = {
            id: String, name: String, aliases: String, department: String?, styles: String,
            links: String, _: Long, _: Long ->
        Soundsystem(id, name, aliases.splitLines(), department, styles.toStyles(), links.splitLines())
    }

    private val artistMapper = {
            id: String, name: String, aliases: String, styles: String, links: String, _: Long, _: Long ->
        Artist(id, name, aliases.splitLines(), styles.toStyles(), links.splitLines())
    }

    fun soundsystems(): Flow<List<Soundsystem>> =
        queries.visibleSoundsystems(soundsystemMapper).asFlow().mapToList(Dispatchers.Default)

    fun artists(): Flow<List<Artist>> =
        queries.visibleArtists(artistMapper).asFlow().mapToList(Dispatchers.Default)

    fun soundsystem(id: String): Soundsystem? = queries.soundsystemById(id, soundsystemMapper).executeAsOneOrNull()

    fun artist(id: String): Artist? = queries.artistById(id, artistMapper).executeAsOneOrNull()

    fun membersOf(soundsystemId: String): List<Artist> = queries.membersOf(soundsystemId, artistMapper).executeAsList()

    fun soundsystemsOf(artistId: String): List<Soundsystem> =
        queries.soundsystemsOf(artistId, soundsystemMapper).executeAsList()

    /** Soundsystems and artists matching [query], best first. Works offline. */
    fun search(query: String): List<DirectoryHit> {
        if (Search.normalize(query).isEmpty()) return emptyList()
        val soundsystems = queries.visibleSoundsystems(soundsystemMapper).executeAsList()
        val artists = queries.visibleArtists(artistMapper).executeAsList()
        return Search.rank(Search.soundsystems(query, soundsystems) + Search.artists(query, artists))
    }

    /** Existing entries that look like [name], shown before creating a new one to avoid duplicates. */
    fun lookalikes(name: String): List<DirectoryHit> = search(name).take(3)

    fun createSoundsystem(
        name: String,
        aliases: List<String> = emptyList(),
        department: String? = null,
        styles: List<Style> = emptyList(),
        links: List<String> = emptyList(),
    ): Soundsystem {
        val soundsystem = Soundsystem(Uuid.random().toString(), name.trim(), aliases, department, styles, links)
        save(soundsystem)
        return soundsystem
    }

    fun createArtist(
        name: String,
        aliases: List<String> = emptyList(),
        styles: List<Style> = emptyList(),
        links: List<String> = emptyList(),
        soundsystemIds: List<String> = emptyList(),
    ): Artist {
        val artist = Artist(Uuid.random().toString(), name.trim(), aliases, styles, links)
        queries.transaction {
            save(artist)
            soundsystemIds.forEach { queries.addMember(it, artist.id) }
        }
        return artist
    }

    internal fun save(soundsystem: Soundsystem) = with(soundsystem) {
        queries.upsertSoundsystem(
            id, name, aliases.joinLines(), department, styles.joinToString("\n") { it.id },
            links.joinLines(), 0, clock().toEpochMilliseconds(),
        )
    }

    internal fun save(artist: Artist) = with(artist) {
        queries.upsertArtist(
            id, name, aliases.joinLines(), styles.joinToString("\n") { it.id },
            links.joinLines(), 0, clock().toEpochMilliseconds(),
        )
    }

    internal fun addMember(soundsystemId: String, artistId: String) = queries.addMember(soundsystemId, artistId)

    /** Fills an empty directory with clearly fictional demo data, for tests and the first launch. */
    fun seedDemoIfEmpty() {
        if (queries.countSoundsystems().executeAsOne() > 0) return
        queries.transaction { DemoData.seed(this@DirectoryRepository) }
    }

    // For Swift: plain callbacks instead of Flows.
    fun watchSoundsystems(onChange: (List<Soundsystem>) -> Unit): Watcher = soundsystems().watch(onChange)

    fun watchArtists(onChange: (List<Artist>) -> Unit): Watcher = artists().watch(onChange)
}

private fun String.toStyles(): List<Style> = splitLines().mapNotNull(Style::fromId)
