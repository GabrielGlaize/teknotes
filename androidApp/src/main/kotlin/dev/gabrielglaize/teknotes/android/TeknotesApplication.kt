package dev.gabrielglaize.teknotes.android

import android.app.Application
import dev.gabrielglaize.teknotes.SupabaseConfig
import dev.gabrielglaize.teknotes.Teknotes
import dev.gabrielglaize.teknotes.createTeknotes

class TeknotesApplication : Application() {
    lateinit var teknotes: Teknotes
        private set

    override fun onCreate() {
        super.onCreate()
        val config = BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() }?.let { SupabaseConfig(it, BuildConfig.SUPABASE_KEY) }
        teknotes = createTeknotes(this, config)
        // Before the first sync (or with no server), the directory starts with the demo data.
        if (BuildConfig.DEBUG) teknotes.directory.seedDemoIfEmpty()
    }
}
