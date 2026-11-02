package dev.gabrielglaize.teknotes

import dev.gabrielglaize.teknotes.db.TeknotesQueries
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

data class SyncState(
    val running: Boolean,
    /** Local changes not on the server yet. */
    val pending: Int,
    /** Epoch milliseconds of the last complete sync, or null if none yet. */
    val lastSyncedAt: Long?,
    /** Message of the last failure, cleared by the next success. */
    val error: String?,
)

/**
 * Two-way sync with Supabase, last write wins.
 * - Directory: anyone receives it; local creations are sent once signed in with a pseudo.
 * - Notebook: sent and received only with an account.
 * A night or entry still waiting in the outbox is never overwritten by the server's copy.
 * What the server no longer has was deleted on another phone, and leaves this one too.
 */
class Sync internal constructor(
    private val remote: SupabaseClient?,
    private val queries: TeknotesQueries,
    private val directory: DirectoryRepository,
    private val notebook: NotebookRepository,
    private val account: () -> Account,
    private val scope: CoroutineScope,
    private val clock: () -> Instant,
) {
    private val mutex = Mutex()
    private var scheduled: Job? = null
    private val mutableState = MutableStateFlow(SyncState(false, pendingCount(), null, null))
    val state: StateFlow<SyncState> = mutableState.asStateFlow()

    /**
     * Syncs a moment after the last change, so a burst of taps makes a single round trip.
     * Also called when the app comes back to the foreground. While changes are still
     * waiting (no network), it tries again, less and less often.
     */
    fun schedule() {
        mutableState.value = mutableState.value.copy(pending = pendingCount())
        if (remote == null) return
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(1_500)
            var wait = FIRST_RETRY_MS
            while (true) {
                try {
                    syncNow()
                    break
                } catch (_: TeknotesException) {
                    if (pendingCount() == 0) break
                }
                delay(wait)
                wait = minOf(wait * 2, LAST_RETRY_MS)
            }
        }
    }

    /** Signed out: the next account's server copy says nothing about what's on this phone. */
    internal fun accountGone() {
        queries.transaction {
            queries.forgetSyncedNights()
            queries.forgetSyncedEntries()
        }
    }

    /** Right after signing in: the whole notebook joins the account. */
    internal fun accountReady() {
        notebook.recordEverything()
        schedule()
    }

    @Throws(TeknotesException::class, CancellationException::class)
    suspend fun syncNow() {
        val client = remote ?: return
        mutex.withLock {
            mutableState.value = mutableState.value.copy(running = true)
            try {
                val refused = if (account().canContribute) push(client) else null
                pullDirectory(client)
                if (account().canContribute) pullNotebook(client)
                mutableState.value = SyncState(false, pendingCount(), clock().toEpochMilliseconds(), refused)
            } catch (error: CancellationException) {
                mutableState.value = mutableState.value.copy(running = false)
                throw error
            } catch (error: Exception) {
                val message = friendlyMessage(error)
                mutableState.value = mutableState.value.copy(running = false, pending = pendingCount(), error = message)
                throw TeknotesException(message, error)
            }
        }
    }

    // For Swift: plain callbacks instead of Flows.
    fun watchState(onChange: (SyncState) -> Unit): Watcher = state.watch(onChange)

    private fun pendingCount() = queries.countPending().executeAsOne().toInt()

    /** Sends the outbox. Returns a message if the server refused some of it. */
    private suspend fun push(client: SupabaseClient): String? {
        var refused: String? = null
        val changes = queries.pendingChanges().executeAsList()
            .sortedWith(compareBy({ Entity.of(it.entity).ordinal }, { it.id }))
        for (change in changes) {
            try {
                send(client, Entity.of(change.entity), change.entity_id, change.operation == Operation.DELETE.key)
            } catch (error: HttpRequestException) {
                throw error // no network: everything stays in the outbox for next time
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Refused by the server (rules, constraint, entry now hidden): retrying would fail forever.
                refused = "Une modification a été refusée par le serveur."
            }
            queries.removeChange(change.id)
        }
        return refused
    }

    private suspend fun send(client: SupabaseClient, entity: Entity, id: String, delete: Boolean) {
        when (entity) {
            Entity.SOUNDSYSTEM -> directory.soundsystem(id)?.let { client.from("soundsystems").upsert(it.toRow()) }
            Entity.ARTIST -> directory.artist(id)?.let { client.from("artists").upsert(it.toRow()) }
            Entity.MEMBERSHIP -> {
                val (soundsystemId, artistId) = id.split(":")
                if (delete) {
                    client.from("memberships").delete {
                        filter {
                            eq("soundsystem_id", soundsystemId)
                            eq("artist_id", artistId)
                        }
                    }
                } else {
                    client.from("memberships").upsert(MembershipRow(soundsystemId, artistId)) { ignoreDuplicates = true }
                }
            }
            Entity.NIGHT ->
                if (delete) {
                    client.from("nights").delete { filter { eq("id", id) } }
                } else {
                    queries.nightById(id).executeAsOneOrNull()?.let { night ->
                        client.from("nights").upsert(
                            NightRow(night.id, night.date, night.department, night.title, night.comment, night.shared != 0L),
                        )
                        queries.markNightSynced(night.id)
                    }
                }
            Entity.NIGHT_ENTRY ->
                if (delete) {
                    client.from("night_entries").delete { filter { eq("id", id) } }
                } else {
                    queries.entryById(id).executeAsOneOrNull()?.let { entry ->
                        client.from("night_entries").upsert(
                            NightEntryRow(entry.id, entry.night_id, entry.soundsystem_id, entry.artist_id, entry.rating?.toInt(), entry.comment),
                        )
                        queries.markEntrySynced(entry.id)
                    }
                }
        }
    }

    /** Reads a whole table page by page: the server returns 1000 rows at most per request. */
    private suspend inline fun <reified T : Any> fetchAll(client: SupabaseClient, table: String, orderBy: String): List<T> {
        val rows = mutableListOf<T>()
        while (true) {
            val page = client.from(table).select {
                order(orderBy, Order.ASCENDING)
                range(rows.size.toLong(), rows.size + PAGE_SIZE - 1L)
            }.decodeList<T>()
            rows += page
            if (page.size < PAGE_SIZE) return rows
        }
    }

    private suspend fun pullDirectory(client: SupabaseClient) {
        val soundsystems = fetchAll<SoundsystemRow>(client, "soundsystems", "id").map { it.toModel() }
        val artists = fetchAll<ArtistRow>(client, "artists", "id").map { it.toModel() }
        val members = fetchAll<MembershipRow>(client, "memberships", "soundsystem_id").map { it.soundsystemId to it.artistId }
        directory.replaceWith(soundsystems, artists, members)
    }

    private suspend fun pullNotebook(client: SupabaseClient) {
        val userId = account().userId() ?: return
        val nights = client.from("nights").select { filter { eq("owner", userId) } }.decodeList<NightRow>()
        val entries = nights.map { it.id }.chunked(50).flatMap { ids ->
            client.from("night_entries").select { filter { isIn("night_id", ids) } }.decodeList<NightEntryRow>()
        }
        val time = clock().toEpochMilliseconds()
        queries.transaction {
            for (night in nights) {
                if (queries.isPending(Entity.NIGHT.key, night.id).executeAsOne() > 0) continue
                val createdAt = queries.nightById(night.id).executeAsOneOrNull()?.created_at ?: time
                queries.upsertNight(night.id, night.date, night.department, night.title, night.comment, if (night.shared) 1 else 0, createdAt, time)
            }
            for (entry in entries) {
                if (queries.isPending(Entity.NIGHT_ENTRY.key, entry.id).executeAsOne() > 0) continue
                if (queries.isPending(Entity.NIGHT.key, entry.nightId).executeAsOne() > 0) continue
                val existing = queries.entryById(entry.id).executeAsOneOrNull()
                val label = entry.soundsystemId?.let { directory.soundsystem(it)?.name }
                    ?: entry.artistId?.let { directory.artist(it)?.name }
                    ?: existing?.label.orEmpty()
                queries.upsertEntry(
                    entry.id, entry.nightId, entry.soundsystemId, entry.artistId, label,
                    entry.rating?.toLong(), entry.comment, existing?.created_at ?: time,
                )
            }
            // What the server had and no longer has was deleted on another phone.
            val nightIds = nights.map { it.id }.toSet()
            for (id in queries.syncedNightIds().executeAsList() - nightIds) {
                if (queries.isPending(Entity.NIGHT.key, id).executeAsOne() > 0) continue
                queries.dropEntryChangesOfNight(id)
                queries.deleteEntriesOfNight(id)
                queries.deleteNight(id)
            }
            val entryIds = entries.map { it.id }.toSet()
            for (id in queries.syncedEntryIds().executeAsList() - entryIds) {
                if (queries.isPending(Entity.NIGHT_ENTRY.key, id).executeAsOne() > 0) continue
                queries.deleteEntry(id)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 1000
        const val FIRST_RETRY_MS = 30_000L
        const val LAST_RETRY_MS = 300_000L
    }
}
