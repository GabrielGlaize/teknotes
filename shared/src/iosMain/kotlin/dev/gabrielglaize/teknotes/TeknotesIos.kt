package dev.gabrielglaize.teknotes

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.gabrielglaize.teknotes.db.TeknotesDatabase

/** Creates the shared core with its database in the app's private storage. In Swift: `TeknotesIosKt.createTeknotes(config:)`. */
fun createTeknotes(config: SupabaseConfig?): Teknotes =
    Teknotes(NativeSqliteDriver(TeknotesDatabase.Schema, "teknotes.db"), config)
