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
    private val outbox: Outbox,
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

    /** Snapshot of the visible soundsystems, for pickers. */
    fun allSoundsystemsNow(): List<Soundsystem> = queries.visibleSoundsystems(soundsystemMapper).executeAsList()

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

    /** Adds a soundsystem to the directory. It reaches the shared directory once an account is there. */
    fun createSoundsystem(
        name: String,
        aliases: List<String>,
        department: String?,
        styles: List<Style>,
        links: List<String>,
    ): Soundsystem {
        require(name.isNotBlank()) { "Le nom est obligatoire" }
        val soundsystem = Soundsystem(Uuid.random().toString(), name.trim(), aliases.clean(), department, styles, links.clean())
        queries.transaction {
            save(soundsystem)
            outbox.record(Entity.SOUNDSYSTEM, soundsystem.id)
        }
        outbox.changed()
        return soundsystem
    }

    /** Adds an artist, optionally as a member of soundsystems. */
    fun createArtist(
        name: String,
        aliases: List<String>,
        styles: List<Style>,
        links: List<String>,
        soundsystemIds: List<String>,
    ): Artist {
        require(name.isNotBlank()) { "Le nom est obligatoire" }
        val artist = Artist(Uuid.random().toString(), name.trim(), aliases.clean(), styles, links.clean())
        queries.transaction {
            save(artist)
            outbox.record(Entity.ARTIST, artist.id)
            soundsystemIds.forEach { soundsystemId ->
                queries.addMember(soundsystemId, artist.id)
                outbox.record(Entity.MEMBERSHIP, membershipKey(soundsystemId, artist.id))
            }
        }
        outbox.changed()
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

    internal fun memberships(): List<Pair<String, String>> =
        queries.allMemberships().executeAsList().map { it.soundsystem_id to it.artist_id }

    /** Replaces the cache with the server's directory, keeping local entries not sent yet. */
    internal fun replaceWith(soundsystems: List<Soundsystem>, artists: List<Artist>, members: List<Pair<String, String>>) {
        val time = clock().toEpochMilliseconds()
        queries.transaction {
            queries.deleteSyncedSoundsystems()
            queries.deleteSyncedArtists()
            queries.deleteSyncedMemberships()
            soundsystems.forEach {
                queries.insertSoundsystemIfAbsent(
                    it.id, it.name, it.aliases.joinLines(), it.department, it.styles.joinToString("\n") { s -> s.id },
                    it.links.joinLines(), 0, time,
                )
            }
            artists.forEach {
                queries.insertArtistIfAbsent(
                    it.id, it.name, it.aliases.joinLines(), it.styles.joinToString("\n") { s -> s.id }, it.links.joinLines(), 0, time,
                )
            }
            members.forEach { (soundsystem, artist) -> queries.addMember(soundsystem, artist) }
        }
    }

    /** Fills an empty directory with clearly fictional demo data, until the first sync. */
    fun seedDemoIfEmpty() {
        if (queries.countSoundsystems().executeAsOne() > 0) return
        queries.transaction { DemoData.seed(this@DirectoryRepository) }
    }

    // For Swift: plain callbacks instead of Flows.
    fun watchSoundsystems(onChange: (List<Soundsystem>) -> Unit): Watcher = soundsystems().watch(onChange)

    fun watchArtists(onChange: (List<Artist>) -> Unit): Watcher = artists().watch(onChange)
}

private fun String.toStyles(): List<Style> = splitLines().mapNotNull(Style::fromId)

private fun List<String>.clean() = map { it.trim() }.filter { it.isNotEmpty() }.distinct()
