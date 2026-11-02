package dev.gabrielglaize.teknotes

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.gabrielglaize.teknotes.db.TeknotesDatabase

/** Creates the shared core with its database in the app's private storage. */
fun createTeknotes(context: Context): Teknotes =
    Teknotes(AndroidSqliteDriver(TeknotesDatabase.Schema, context, "teknotes.db"))
