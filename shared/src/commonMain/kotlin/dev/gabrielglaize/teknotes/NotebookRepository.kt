package dev.gabrielglaize.teknotes

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.gabrielglaize.teknotes.db.TeknotesQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
) {
    private val nightMapper = {
            id: String, date: String, department: String?, title: String, comment: String, shared: Long,
            _: Long, _: Long, entryCount: Long ->
        Night(id, date, department, title, comment, shared != 0L, entryCount.toInt())
    }

    private val entryMapper = {
            id: String, nightId: String, soundsystemId: String?, artistId: String?, _: String, rating: Long?,
            comment: String, _: Long, label: String ->
        NightEntry(id, nightId, soundsystemId, artistId, label, rating?.toInt(), comment)
    }

    private fun now() = clock().toEpochMilliseconds()

    private fun touched(nightId: String) {
        outbox.record(Entity.NIGHT, nightId)
    }

    fun nights(): Flow<List<Night>> = queries.nightSummaries(nightMapper).asFlow().mapToList(Dispatchers.Default)

    fun entries(nightId: String): Flow<List<NightEntry>> =
        queries.entriesOfNight(nightId, entryMapper).asFlow().mapToList(Dispatchers.Default)

    fun night(id: String): Night? = queries.nightSummaries(nightMapper).executeAsList().firstOrNull { it.id == id }

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
        val unchanged = current.department == department && current.title == title.trim() &&
            current.comment == comment.trim() && (current.shared != 0L) == shared
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

    /** Adds a soundsystem or an artist found in the directory to a night: the second tap of "add in 2 taps". */
    fun addEntry(nightId: String, hit: DirectoryHit): String {
        val id = Uuid.random().toString()
        val soundsystemId = hit.id.takeIf { hit.kind == DirectoryHit.Kind.SOUNDSYSTEM }
        val artistId = hit.id.takeIf { hit.kind == DirectoryHit.Kind.ARTIST }
        queries.transaction {
            queries.insertEntry(id, nightId, soundsystemId, artistId, hit.name, null, "", now())
            outbox.record(Entity.NIGHT_ENTRY, id)
        }
        outbox.changed()
        return id
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
        val nights = queries.nightSummaries(nightMapper).executeAsList().map { night ->
            ExportedNight(
                date = night.date,
                department = Departments.byCode(night.department)?.label,
                title = night.title,
                comment = night.comment,
                shared = night.shared,
                heard = entries[night.id].orEmpty().map { ExportedEntry(it.label, it.kind(), it.rating, it.comment) },
            )
        }
        return exportFormat.encodeToString(ExportedNotebook.serializer(), ExportedNotebook(nights))
    }

    /** The whole notebook as CSV, one line per thing heard (or per night when nothing was noted). */
    fun exportCsv(): String {
        val entries = queries.allEntries(entryMapper).executeAsList().groupBy { it.nightId }
        val lines = mutableListOf("date;departement;titre;nom;type;note;commentaire")
        for (night in queries.nightSummaries(nightMapper).executeAsList()) {
            val department = Departments.byCode(night.department)?.label.orEmpty()
            val heard = entries[night.id].orEmpty()
            if (heard.isEmpty()) {
                lines += csv(night.date, department, night.title, "", "", "", night.comment)
            }
            heard.forEach { entry ->
                lines += csv(night.date, department, night.title, entry.label, entry.kind(), entry.rating?.toString().orEmpty(), entry.comment)
            }
        }
        return lines.joinToString("\n")
    }

    // For Swift: plain callbacks instead of Flows.
    fun watchNights(onChange: (List<Night>) -> Unit): Watcher = nights().watch(onChange)

    fun watchEntries(nightId: String, onChange: (List<NightEntry>) -> Unit): Watcher = entries(nightId).watch(onChange)

    private fun NightEntry.kind() = if (soundsystemId != null) "soundsystem" else "artiste"

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
private data class ExportedEntry(val name: String, val type: String, val rating: Int?, val comment: String)
