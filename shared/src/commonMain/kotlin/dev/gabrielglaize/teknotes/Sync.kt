package dev.gabrielglaize.teknotes

import dev.gabrielglaize.teknotes.db.TeknotesQueries
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
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
 * Deletions made on another phone are not brought back (V1).
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

    /** Syncs a moment after the last change, so a burst of taps makes a single round trip. */
    fun schedule() {
        mutableState.value = mutableState.value.copy(pending = pendingCount())
        if (remote == null) return
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(1_500)
            runCatching { syncNow() }
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
                if (account().canContribute) push(client)
                pullDirectory(client)
                if (account().canContribute) pullNotebook(client)
                mutableState.value = SyncState(false, pendingCount(), clock().toEpochMilliseconds(), null)
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

    private suspend fun push(client: SupabaseClient) {
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
                // Refused by the server (rules, constraint): retrying would fail forever.
                mutableState.value = mutableState.value.copy(error = friendlyMessage(error))
            }
            queries.removeChange(change.id)
        }
    }

    private suspend fun send(client: SupabaseClient, entity: Entity, id: String, delete: Boolean) {
        when (entity) {
            Entity.SOUNDSYSTEM -> directory.soundsystem(id)?.let { client.from("soundsystems").upsert(it.toRow()) }
            Entity.ARTIST -> directory.artist(id)?.let { client.from("artists").upsert(it.toRow()) }
            Entity.MEMBERSHIP -> {
                val (soundsystemId, artistId) = id.split(":")
                client.from("memberships").upsert(MembershipRow(soundsystemId, artistId)) { ignoreDuplicates = true }
            }
            Entity.NIGHT ->
                if (delete) {
                    client.from("nights").delete { filter { eq("id", id) } }
                } else {
                    queries.nightById(id).executeAsOneOrNull()?.let { night ->
                        client.from("nights").upsert(
                            NightRow(night.id, night.date, night.department, night.title, night.comment, night.shared != 0L),
                        )
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
                    }
                }
        }
    }

    private suspend fun pullDirectory(client: SupabaseClient) {
        val soundsystems = client.from("soundsystems").select().decodeList<SoundsystemRow>().map { it.toModel() }
        val artists = client.from("artists").select().decodeList<ArtistRow>().map { it.toModel() }
        val members = client.from("memberships").select().decodeList<MembershipRow>().map { it.soundsystemId to it.artistId }
        directory.replaceWith(soundsystems, artists, members)
    }

    private suspend fun pullNotebook(client: SupabaseClient) {
        val userId = account().userId() ?: return
        val nights = client.from("nights").select { filter { eq("owner", userId) } }.decodeList<NightRow>()
        if (nights.isEmpty()) return
        val entries = client.from("night_entries").select { filter { isIn("night_id", nights.map { it.id }) } }
            .decodeList<NightEntryRow>()
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
        }
    }
}
