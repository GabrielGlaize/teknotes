package dev.gabrielglaize.teknotes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.gabrielglaize.teknotes.db.TeknotesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** The user's own notebook: nights and what was heard. Always local first. */
class NotebookRepository internal constructor(
    private val queries: TeknotesQueries,
    private val outbox: Outbox,
    private val clock: () -> Instant,
    /** Sharing a night makes it public: it takes an account. */
    private val canShare: () -> Boolean,
) {
    private val entryMapper = {
            id: String, nightId: String, soundsystemId: String?, artistId: String?, rating: Long?,
            comment: String, stored: String, artistName: String?, soundsystemName: String? ->
        // A name no longer in the directory (hidden, merged) falls back on the one kept when it was added.
        val gone = (soundsystemId != null && soundsystemName == null) || (artistId != null && artistName == null)
        val label = if (gone) stored else setLabel(artistName, soundsystemName)
        NightEntry(id, nightId, soundsystemId, artistId, artistName, soundsystemName, label, rating?.toInt(), comment)
    }

    private fun readNights(): List<Night> {
        val entries = queries.allEntries(entryMapper).executeAsList().bySoundsystem().groupBy { it.nightId }
        return queries.nightSummaries { id, date, department, title, comment, shared, entryCount ->
            val heard = entries[id].orEmpty()
            val ratings = heard.mapNotNull { it.rating }
            Night(
                id, date, department, title, comment, shared != 0L, entryCount.toInt(), heard.map { it.label },
                ratings.takeIf { it.isNotEmpty() }?.average(),
            )
        }.executeAsList()
    }

    /** Sets of a same soundsystem stay together, in the order the soundsystems were first heard. */
    private fun List<NightEntry>.bySoundsystem(): List<NightEntry> =
        groupBy { it.nightId to (it.soundsystemId ?: it.id) }.values.flatten()

    private fun now() = clock().toEpochMilliseconds()

    private fun touched(nightId: String) {
        outbox.record(Entity.NIGHT, nightId)
    }

    // The summaries read the entries too: any change of either table refreshes the list.
    fun nights(): Flow<List<Night>> = queries.nightSummaries().asFlow().map { readNights() }.flowOn(Dispatchers.Default)

    fun entries(nightId: String): Flow<List<NightEntry>> =
        queries.entriesOfNight(nightId, entryMapper).asFlow().mapToList(Dispatchers.Default).map { it.bySoundsystem() }

    fun night(id: String): Night? = readNights().firstOrNull { it.id == id }

    /** Personal directory: every soundsystem and artist the user noted. No account or network needed. */
    fun myDirectory(): Flow<List<MyDirectoryEntry>> =
        queries.mySoundsystems(mySoundsystemMapper).asFlow().mapToList(Dispatchers.Default)
            .combine(queries.myArtists(myArtistMapper).asFlow().mapToList(Dispatchers.Default)) { a, b -> a + b }

    private val mySoundsystemMapper = { id: String, name: String, nights: Long, average: Double?, lastDate: String? ->
        MyDirectoryEntry(id, DirectoryHit.Kind.SOUNDSYSTEM, name, nights.toInt(), average, lastDate.orEmpty())
    }

    private val myArtistMapper = { id: String, name: String, nights: Long, average: Double?, lastDate: String? ->
        MyDirectoryEntry(id, DirectoryHit.Kind.ARTIST, name, nights.toInt(), average, lastDate.orEmpty())
    }

    /**
     * The user's ranking: best average first, then the most heard; what isn't rated comes last.
     * [kind]: only artists or only soundsystems, or null for both.
     */
    fun ranked(items: List<MyDirectoryEntry>, kind: DirectoryHit.Kind?, query: String): List<MyDirectoryEntry> =
        items.filter { (kind == null || it.kind == kind) && (Search.normalize(query).isEmpty() || Search.score(query, it.name) != null) }
            .sortedWith(
                compareByDescending<MyDirectoryEntry> { it.average ?: -1.0 }
                    .thenByDescending { it.nights }
                    .thenBy { it.name.lowercase() },
            )

    /** Everything the user noted about a soundsystem or an artist, newest first. */
    fun notesOn(kind: DirectoryHit.Kind, id: String): Flow<List<MyNote>> {
        val mapper = { entryId: String, nightId: String, soundsystemId: String?, artistId: String?, rating: Long?,
                comment: String, stored: String, artistName: String?, soundsystemName: String?, date: String, title: String ->
            MyNote(entryMapper(entryId, nightId, soundsystemId, artistId, rating, comment, stored, artistName, soundsystemName), date, title)
        }
        val query = if (kind == DirectoryHit.Kind.SOUNDSYSTEM) queries.notesOnSoundsystem(id, mapper) else queries.notesOnArtist(id, mapper)
        return query.asFlow().mapToList(Dispatchers.Default)
    }

    /**
     * "I'm here": opens the night in progress, or creates it. Before noon, the night
     * of the day before is still in progress (see [NightClock]).
     */
    fun checkIn(department: String?): Night {
        val local = clock().toLocalDateTime(TimeZone.currentSystemDefault())
        val date = NightClock.nightDate(local)
        val existing = queries.nightByDate(date).executeAsOneOrNull()
        val id = existing?.id ?: createNight(date, department, "").id
        return night(id)!!
    }

    fun createNight(date: String, department: String?, title: String): Night {
        LocalDate.parse(date) // rejects anything that isn't an ISO date
        val id = Uuid.random().toString()
        val time = now()
        queries.transaction {
            queries.insertNight(id, date, department, title.trim(), "", 0, time, time)
            touched(id)
        }
        outbox.changed()
        return night(id)!!
    }

    fun updateNight(id: String, department: String?, title: String, comment: String, shared: Boolean) {
        val current = queries.nightById(id).executeAsOneOrNull() ?: return
        val wasShared = current.shared != 0L
        val shared = shared && (wasShared || canShare())
        val unchanged = current.department == department && current.title == title.trim() &&
            current.comment == comment.trim() && wasShared == shared
        if (unchanged) return // screens save on every keystroke and when they open
        queries.transaction {
            queries.updateNight(department, title.trim(), comment.trim(), if (shared) 1 else 0, now(), id)
            touched(id)
        }
        outbox.changed()
    }

    fun deleteNight(id: String) {
        queries.transaction {
            queries.dropEntryChangesOfNight(id)
            queries.deleteEntriesOfNight(id)
            queries.deleteNight(id)
            outbox.record(Entity.NIGHT, id, Operation.DELETE)
        }
        outbox.changed()
    }

    /** Adds a set to a night: the second tap of "add in 2 taps". */
    fun addSet(nightId: String, choice: SetChoice): String {
        val id = Uuid.random().toString()
        queries.transaction {
            queries.insertEntry(id, nightId, choice.soundsystemId, choice.artistId, choice.label, null, "", now())
            outbox.record(Entity.NIGHT_ENTRY, id)
        }
        outbox.changed()
        return id
    }

    fun addEntry(nightId: String, hit: DirectoryHit): String = addSet(nightId, hit.toChoice())

    /** Sets or changes who played, or on which soundsystem, keeping the rating and the comment. */
    fun changeSet(entryId: String, hit: DirectoryHit) {
        val current = queries.entriesOfNightById(entryId, entryMapper).executeAsOneOrNull() ?: return
        val choice = when (hit.kind) {
            DirectoryHit.Kind.SOUNDSYSTEM -> SetChoice(current.artistId, current.artistName, hit.id, hit.name)
            DirectoryHit.Kind.ARTIST -> SetChoice(hit.id, hit.name, current.soundsystemId, current.soundsystemName)
        }
        queries.transaction {
            queries.updateEntrySet(choice.soundsystemId, choice.artistId, choice.label, entryId)
            outbox.record(Entity.NIGHT_ENTRY, entryId)
        }
        outbox.changed()
    }

    private fun DirectoryHit.toChoice() = when (kind) {
        DirectoryHit.Kind.SOUNDSYSTEM -> SetChoice(null, null, id, name)
        DirectoryHit.Kind.ARTIST -> SetChoice(id, name, null, null)
    }

    /** Rating (1 to 5, or null to clear it) and comment of something heard. */
    fun updateEntry(entryId: String, rating: Int?, comment: String) {
        require(rating == null || rating in 1..5) { "La note va de 1 à 5" }
        val current = queries.entryById(entryId).executeAsOneOrNull() ?: return
        if (current.rating?.toInt() == rating && current.comment == comment.trim()) return
        queries.transaction {
            queries.updateEntry(rating?.toLong(), comment.trim(), entryId)
            outbox.record(Entity.NIGHT_ENTRY, entryId)
        }
        outbox.changed()
    }

    fun removeEntry(entryId: String) {
        queries.transaction {
            queries.deleteEntry(entryId)
            outbox.record(Entity.NIGHT_ENTRY, entryId, Operation.DELETE)
        }
        outbox.changed()
    }

    /** Puts the whole notebook in the outbox, e.g. right after signing in. */
    internal fun recordEverything() = queries.transaction {
        queries.allNights().executeAsList().forEach { outbox.record(Entity.NIGHT, it.id) }
        queries.allEntriesRaw().executeAsList().forEach { outbox.record(Entity.NIGHT_ENTRY, it.id) }
    }

    /** The whole notebook as JSON: the user's data belongs to them. */
    fun exportJson(): String {
        val entries = queries.allEntries(entryMapper).executeAsList().groupBy { it.nightId }
        val nights = readNights().map { night ->
            ExportedNight(
                date = night.date,
                department = Departments.byCode(night.department)?.label,
                title = night.title,
                comment = night.comment,
                shared = night.shared,
                heard = entries[night.id].orEmpty().map { ExportedEntry(it.artistName, it.soundsystemName, it.rating, it.comment) },
            )
        }
        return exportFormat.encodeToString(ExportedNotebook.serializer(), ExportedNotebook(nights))
    }

    /** The whole notebook as CSV, one line per thing heard (or per night when nothing was noted). */
    fun exportCsv(): String {
        val entries = queries.allEntries(entryMapper).executeAsList().groupBy { it.nightId }
        val lines = mutableListOf("date;departement;titre;artiste;soundsystem;note;commentaire")
        for (night in readNights()) {
            val department = Departments.byCode(night.department)?.label.orEmpty()
            val heard = entries[night.id].orEmpty()
            if (heard.isEmpty()) {
                lines += csv(night.date, department, night.title, "", "", "", night.comment)
            }
            heard.forEach { entry ->
                lines += csv(night.date, department, night.title, entry.artistName.orEmpty(), entry.soundsystemName.orEmpty(), entry.rating?.toString().orEmpty(), entry.comment)
            }
        }
        return lines.joinToString("\n")
    }

    // For Swift: plain callbacks instead of Flows.
    fun watchNights(onChange: (List<Night>) -> Unit): Watcher = nights().watch(onChange)

    fun watchEntries(nightId: String, onChange: (List<NightEntry>) -> Unit): Watcher = entries(nightId).watch(onChange)

    fun watchMyDirectory(onChange: (List<MyDirectoryEntry>) -> Unit): Watcher = myDirectory().watch(onChange)

    fun watchNotesOn(kind: DirectoryHit.Kind, id: String, onChange: (List<MyNote>) -> Unit): Watcher = notesOn(kind, id).watch(onChange)

    private fun csv(vararg cells: String) = cells.joinToString(";") { cell ->
        if (cell.any { it == ';' || it == '"' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
    }

    private val exportFormat = Json { prettyPrint = true }
}

@Serializable
private data class ExportedNotebook(val nights: List<ExportedNight>)

@Serializable
private data class ExportedNight(
    val date: String,
    val department: String?,
    val title: String,
    val comment: String,
    val shared: Boolean,
    val heard: List<ExportedEntry>,
)

@Serializable
private data class ExportedEntry(val artist: String?, val soundsystem: String?, val rating: Int?, val comment: String)
