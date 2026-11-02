package dev.gabrielglaize.teknotes

import app.cash.sqldelight.db.SqlDriver
import dev.gabrielglaize.teknotes.db.TeknotesDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Entry point of the shared core, created once per app
 * (createTeknotes(context, config) on Android, createTeknotes(config) on iOS).
 * Without a [SupabaseConfig], everything works offline and the account is unavailable.
 */
class Teknotes internal constructor(
    driver: SqlDriver,
    config: SupabaseConfig?,
    clock: () -> Instant,
    inMemorySession: Boolean,
) {
    constructor(driver: SqlDriver, config: SupabaseConfig?, clock: () -> Instant = { Clock.System.now() }) :
        this(driver, config, clock, inMemorySession = false)

    private val database = TeknotesDatabase(driver)
    private val queries = database.teknotesQueries
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outbox = Outbox(queries, clock)
    private val remote = config?.let { createRemote(it, inMemorySession) }

    val directory = DirectoryRepository(queries, outbox, clock)
    val notebook = NotebookRepository(queries, outbox, clock)
    val settings = SettingsRepository(queries)
    val sync: Sync = Sync(remote, queries, directory, notebook, { account }, scope, clock)
    val account = Account(remote, scope, onSignedIn = { sync.accountReady() })

    init {
        outbox.onChange = { sync.schedule() }
        sync.schedule()
    }

    internal fun pendingChanges(): List<String> =
        queries.pendingChanges().executeAsList().map { "${it.entity}:${it.entity_id}" }
}
