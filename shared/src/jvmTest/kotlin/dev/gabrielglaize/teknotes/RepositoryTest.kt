package dev.gabrielglaize.teknotes

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.gabrielglaize.teknotes.db.TeknotesDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private var now = LocalDateTime(2026, 10, 31, 23, 0)

    private fun teknotes(): Teknotes {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TeknotesDatabase.Schema.create(driver)
        return Teknotes(driver, config = null) { now.toInstant(TimeZone.currentSystemDefault()) }
    }

    @Test
    fun checkInOpensTheSameNightUntilNoon() = runTest {
        val app = teknotes()
        val evening = app.notebook.checkIn("59")
        now = LocalDateTime(2026, 11, 1, 4, 30)
        val lateNight = app.notebook.checkIn(null)
        now = LocalDateTime(2026, 11, 1, 22, 0)
        val nextEvening = app.notebook.checkIn(null)

        assertEquals(evening.id, lateNight.id)
        assertEquals("2026-10-31", evening.date)
        assertEquals("59", evening.department)
        assertEquals("2026-11-01", nextEvening.date)
        assertEquals(listOf("2026-11-01", "2026-10-31"), app.notebook.nights().first().map { it.date })
    }

    @Test
    fun entriesAreAddedFromSearchRatedAndCounted() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        val hit = app.directory.search("tekno krat").first()
        val entryId = app.notebook.addEntry(night.id, hit)
        app.notebook.updateEntry(entryId, 5, "  Montée de malade  ")

        val entries = app.notebook.entries(night.id).first()
        assertEquals("Teknokrates", entries.single().label)
        assertEquals(5, entries.single().rating)
        assertEquals("Montée de malade", entries.single().comment)
        assertEquals(1, app.notebook.nights().first().single().entryCount)
        assertFailsWith<IllegalArgumentException> { app.notebook.updateEntry(entryId, 6, "") }
    }

    @Test
    fun deletingANightDeletesItsEntries() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        app.notebook.addEntry(night.id, app.directory.search("Rouille").first())
        app.notebook.deleteNight(night.id)

        assertTrue(app.notebook.nights().first().isEmpty())
        assertTrue(app.notebook.entries(night.id).first().isEmpty())
    }

    @Test
    fun lookalikesWarnBeforeCreatingADuplicate() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        assertEquals("Caisson Noir", app.directory.lookalikes("Kaisson Noir").first().name)
        assertTrue(app.directory.lookalikes("Soundsystem totalement nouveau").isEmpty())
    }

    @Test
    fun artistsKeepTheirSoundsystems() = runTest {
        val app = teknotes()
        val soundsystem = app.directory.createSoundsystem("Kick Familial", emptyList(), "13", listOf(Style.TRIBE), emptyList())
        val artist = app.directory.createArtist("Dj Galet", emptyList(), emptyList(), emptyList(), listOf(soundsystem.id))

        assertEquals(listOf("Dj Galet"), app.directory.membersOf(soundsystem.id).map { it.name })
        assertEquals(listOf("Kick Familial"), app.directory.soundsystemsOf(artist.id).map { it.name })
        assertEquals(listOf(Style.TRIBE), app.directory.soundsystem(soundsystem.id)?.styles)
    }

    @Test
    fun seedingHappensOnlyOnce() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        app.directory.seedDemoIfEmpty()
        assertEquals(8, app.directory.soundsystems().first().size)
    }

    @Test
    fun exportsContainEveryNightAndEntry() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn("29")
        app.notebook.addEntry(night.id, app.directory.search("Lunatek").first())
        app.notebook.createNight("2026-09-12", null, "")

        val json = app.notebook.exportJson()
        assertTrue("\"Lunatek\"" in json && "29 · Finistère" in json && "2026-09-12" in json)
        val csv = app.notebook.exportCsv().lines()
        assertEquals("date;departement;titre;nom;type;note;commentaire", csv.first())
        assertEquals(3, csv.size)
    }

    private fun Teknotes.pending(): List<String> =
        TeknotesDatabaseAccess.pending(this)

    @Test
    fun everyLocalChangeWaitsInTheOutbox() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn("59")
        val entry = app.notebook.addEntry(night.id, app.directory.search("Rouille").first())
        app.notebook.updateEntry(entry, 4, "")
        val created = app.directory.createSoundsystem("Kick Familial", emptyList(), null, emptyList(), emptyList())

        assertEquals(
            setOf("night:${night.id}", "night_entry:$entry", "soundsystem:${created.id}"),
            app.pending().toSet(),
            "one line per changed row, however many times it changed",
        )
        assertEquals(3, app.sync.state.value.pending)
    }

    @Test
    fun deletingANightAlsoDropsItsUnsentEntries() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        app.notebook.addEntry(night.id, app.directory.search("Rouille").first())
        app.notebook.deleteNight(night.id)

        assertEquals(listOf("night:${night.id}"), app.pending())
    }

    @Test
    fun aRefreshFromTheServerKeepsWhatWasNotSentYet() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val mine = app.directory.createSoundsystem("Pas encore envoyé", emptyList(), null, emptyList(), emptyList())
        val fromServer = Soundsystem("5d000000-0000-4000-8000-000000000099", "Venu du serveur", emptyList(), "75", listOf(Style.ACID), emptyList())

        app.directory.replaceWith(listOf(fromServer), emptyList(), emptyList())

        val names = app.directory.soundsystems().first().map { it.name }
        assertEquals(listOf("Pas encore envoyé", "Venu du serveur"), names)
        assertNull(app.directory.soundsystem("5d000000-0000-4000-8000-000000000001"), "entries gone from the server leave the cache")
        assertEquals("Pas encore envoyé", app.directory.soundsystem(mine.id)?.name)
    }

    @Test
    fun anEntryKeepsItsNameWhenItsSoundsystemLeavesTheDirectory() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        app.notebook.addEntry(night.id, app.directory.search("Caisson Noir").first())
        app.directory.replaceWith(emptyList(), emptyList(), emptyList())

        assertEquals("Caisson Noir", app.notebook.entries(night.id).first().single().label)
    }

    @Test
    fun preferencesStartWithTheSystemAppearance() = runTest {
        val app = teknotes()
        assertEquals(Preferences(Appearance.SYSTEM, fieldMode = false), app.settings.preferences().first())
        app.settings.setAppearance(Appearance.DARK)
        app.settings.setFieldMode(true)
        assertEquals(Preferences(Appearance.DARK, fieldMode = true), app.settings.current())
        assertFalse(app.account.canContribute)
        assertEquals(AccountState.Unavailable, app.account.state.value)
    }

    @Test
    fun editingAnArtistSendsOnlyWhatChangedInItsSoundsystems() = runTest {
        val app = teknotes()
        val kept = app.directory.createSoundsystem("Gardé", emptyList(), null, emptyList(), emptyList())
        val left = app.directory.createSoundsystem("Quitté", emptyList(), null, emptyList(), emptyList())
        val joined = app.directory.createSoundsystem("Rejoint", emptyList(), null, emptyList(), emptyList())
        val artist = app.directory.createArtist("Dj Galet", emptyList(), emptyList(), emptyList(), listOf(kept.id, left.id))

        app.directory.updateArtist(artist.id, "  Dj Galet Bis ", listOf("Galet", " "), listOf(Style.DUB), emptyList(), listOf(kept.id, joined.id))

        assertEquals("Dj Galet Bis", app.directory.artist(artist.id)?.name)
        assertEquals(listOf("Galet"), app.directory.artist(artist.id)?.aliases)
        assertEquals(setOf("Gardé", "Rejoint"), app.directory.soundsystemsOf(artist.id).map { it.name }.toSet())
        assertTrue("membership:${left.id}:${artist.id}" in app.pending())
        assertTrue("membership:${joined.id}:${artist.id}" in app.pending())
    }

    @Test
    fun aNameIsRequiredToEditAnEntry() = runTest {
        val app = teknotes()
        val soundsystem = app.directory.createSoundsystem("Son", emptyList(), null, emptyList(), emptyList())
        assertFailsWith<IllegalArgumentException> {
            app.directory.updateSoundsystem(soundsystem.id, "  ", emptyList(), null, emptyList(), emptyList())
        }
    }

    @Test
    fun savingWithoutChangeDoesNotQueueAnything() = runTest {
        val app = teknotes()
        val night = app.notebook.checkIn("59")
        val before = app.pending().size
        app.notebook.updateNight(night.id, "59", "", "", false)
        assertEquals(before, app.pending().size)
        assertEquals(before, app.sync.state.value.pending)
    }

    @Test
    fun aPhoneWithAnOlderDatabaseIsMigratedWithoutLosingItsNotebook() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "CREATE TABLE night (id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL, department TEXT, title TEXT NOT NULL DEFAULT '', comment TEXT NOT NULL DEFAULT '', shared INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE night_entry (id TEXT NOT NULL PRIMARY KEY, night_id TEXT NOT NULL, soundsystem_id TEXT, artist_id TEXT, label TEXT NOT NULL DEFAULT '', rating INTEGER, comment TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)", 0)
        driver.execute(null, "INSERT INTO night (id, date, created_at, updated_at) VALUES ('old', '2026-10-01', 0, 0)", 0)

        TeknotesDatabase.Schema.migrate(driver, 1, TeknotesDatabase.Schema.version)

        val synced = driver.executeQuery(null, "SELECT synced FROM night WHERE id = 'old'", { cursor ->
            cursor.next()
            app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0))
        }, 0).value
        assertEquals(0L, synced)
    }

    @Test
    fun withoutNetworkTheChangesWaitAndTheMessageIsPlain() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TeknotesDatabase.Schema.create(driver)
        val app = Teknotes(driver, SupabaseConfig("http://127.0.0.1:1", "cle"), { kotlin.time.Clock.System.now() }, inMemorySession = true)
        app.notebook.checkIn("59")

        val error = assertFailsWith<TeknotesException> { app.sync.syncNow() }

        assertEquals("Pas de réseau. Réessaie quand tu captes.", error.message)
        assertEquals(1, app.sync.state.value.pending)
        assertEquals("Pas de réseau. Réessaie quand tu captes.", app.sync.state.value.error)
    }
}
