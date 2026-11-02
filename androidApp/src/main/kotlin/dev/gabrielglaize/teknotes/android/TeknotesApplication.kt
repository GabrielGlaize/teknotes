package dev.gabrielglaize.teknotes.android

import android.app.Application
import dev.gabrielglaize.teknotes.Teknotes
import dev.gabrielglaize.teknotes.createTeknotes

class TeknotesApplication : Application() {
    lateinit var teknotes: Teknotes
        private set

    override fun onCreate() {
        super.onCreate()
        teknotes = createTeknotes(this)
        // Until the sync with Supabase exists, the directory starts with the demo data.
        if (BuildConfig.DEBUG) teknotes.directory.seedDemoIfEmpty()
    }
}
