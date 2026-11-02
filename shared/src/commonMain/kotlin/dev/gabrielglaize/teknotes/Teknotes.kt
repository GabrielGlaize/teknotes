package dev.gabrielglaize.teknotes

import app.cash.sqldelight.db.SqlDriver
import dev.gabrielglaize.teknotes.db.TeknotesDatabase
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Entry point of the shared core, created once per app
 * (createTeknotes(context) on Android, createTeknotes() on iOS).
 */
class Teknotes(driver: SqlDriver, clock: () -> Instant = { Clock.System.now() }) {
    private val database = TeknotesDatabase(driver)

    val directory = DirectoryRepository(database.teknotesQueries, clock)
    val notebook = NotebookRepository(database.teknotesQueries, clock)
}
