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
    fun aSetIsAnArtistOnASoundsystem() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)

        val choices = app.directory.setChoices("rouille").map { it.label }
        assertEquals(listOf("Rouille (Caisson Noir)", "Rouille"), choices)
        val onSoundsystem = app.directory.setChoices("caisson noir").map { it.label }
        assertEquals(listOf("Caisson Noir", "Pyl (Caisson Noir)", "Rouille (Caisson Noir)"), onSoundsystem)

        app.notebook.addSet(night.id, app.directory.setChoices("rouille").first())
        assertEquals("Rouille (Caisson Noir)", app.notebook.entries(night.id).first().single().label)
        assertEquals(listOf("Rouille (Caisson Noir)"), app.notebook.night(night.id)?.sets)
    }

    @Test
    fun aGuestIsAttachedToAnotherSoundsystemWithoutTouchingTheDirectory() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        val id = app.notebook.addEntry(night.id, app.directory.search("Rouille").first())
        app.notebook.updateEntry(id, 4, "Gros set")

        app.notebook.changeSet(id, app.directory.search("Mur du Nord").first())

        val entry = app.notebook.entries(night.id).first().single()
        assertEquals("Rouille (Mur du Nord)", entry.label)
        assertEquals(4, entry.rating)
        assertEquals(listOf("Caisson Noir"), app.directory.soundsystemsOf(entry.artistId!!).map { it.name })
    }

    @Test
    fun setsOfASameSoundsystemStayTogether() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        listOf("Rouille", "Lunatek", "Pyl").forEach { name ->
            app.notebook.addSet(night.id, app.directory.setChoices(name).first())
        }
        assertEquals(
            listOf("Rouille (Caisson Noir)", "Pyl (Caisson Noir)", "Lunatek (Les Ondes Sauvages)"),
            app.notebook.entries(night.id).first().map { it.label },
        )
    }

    @Test
    fun thePersonalDirectoryGathersEverythingNotedOnAnEntry() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val one = app.notebook.createNight("2026-09-12", null, "Bord de l'eau")
        val two = app.notebook.createNight("2026-10-03", null, "")
        val rouille = app.directory.setChoices("rouille").first()
        app.notebook.updateEntry(app.notebook.addSet(one.id, rouille), 5, "Énorme")
        app.notebook.updateEntry(app.notebook.addSet(two.id, rouille), 2, "Moyen")
        app.notebook.addSet(two.id, app.directory.setChoices("lunatek").last())

        val mine = app.notebook.myDirectory().first()
        val artist = mine.first { it.name == "Rouille" }
        assertEquals(2, artist.nights)
        assertEquals(3.5, artist.average)
        assertEquals("2026-10-03", artist.lastDate)
        assertEquals(setOf("Caisson Noir"), mine.filter { it.kind == DirectoryHit.Kind.SOUNDSYSTEM }.map { it.name }.toSet())

        val notes = app.notebook.notesOn(DirectoryHit.Kind.ARTIST, rouille.artistId!!).first()
        assertEquals(listOf("Moyen", "Énorme"), notes.map { it.entry.comment })
        assertEquals("Bord de l'eau", notes.last().nightTitle)

        val ranking = app.notebook.ranked(mine, null, "")
        assertEquals(setOf("Rouille", "Caisson Noir"), ranking.take(2).map { it.name }.toSet())
        assertEquals("Lunatek", ranking.last().name, "not rated: last")
        assertEquals(listOf("Rouille", "Lunatek"), app.notebook.ranked(mine, DirectoryHit.Kind.ARTIST, "").map { it.name })
        assertEquals(emptyList(), app.notebook.ranked(mine, null, "zzzz"))
    }

    @Test
    fun aNightKnowsItsAverage() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn(null)
        assertEquals(null, app.notebook.night(night.id)?.average)
        app.notebook.updateEntry(app.notebook.addSet(night.id, app.directory.setChoices("rouille").first()), 5, "")
        app.notebook.updateEntry(app.notebook.addSet(night.id, app.directory.setChoices("pyl").first()), 2, "")
        assertEquals(3.5, app.notebook.night(night.id)?.average)
    }

    @Test
    fun thePasscodeOpensTheAppAndTooManyTriesMakeYouWait() = runTest {
        var now = kotlin.time.Instant.fromEpochMilliseconds(1_000_000)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TeknotesDatabase.Schema.create(driver)
        val app = Teknotes(driver, null, { now })
        assertFalse(app.settings.hasPasscode)
        assertFalse(app.settings.tutorialDone)
        assertFailsWith<IllegalArgumentException> { app.settings.setPasscode("12a") }

        app.settings.setPasscode("4821")
        assertTrue(app.settings.hasPasscode)
        assertTrue(app.settings.checkPasscode("4821"))
        repeat(5) { assertFalse(app.settings.checkPasscode("0000")) }
        assertEquals(30, app.settings.passcodeWaitSeconds())
        assertFalse(app.settings.checkPasscode("4821"), "even the right code waits")

        now = kotlin.time.Instant.fromEpochMilliseconds(1_031_000)
        assertEquals(0, app.settings.passcodeWaitSeconds())
        assertTrue(app.settings.checkPasscode("4821"))

        app.settings.removePasscode()
        assertFalse(app.settings.hasPasscode)
    }

    @Test
    fun theHashIsARealSha256() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex(Sha256.hash("abc".encodeToByteArray())),
        )
    }

    @Test
    fun sharingANightTakesAnAccount() = runTest {
        val app = teknotes()
        val night = app.notebook.checkIn(null)
        app.notebook.updateNight(night.id, null, "Titre", "", shared = true)
        val after = app.notebook.night(night.id)!!
        assertFalse(after.shared)
        assertEquals("Titre", after.title)
    }

    @Test
    fun anyoneCanAddAStyleWithoutCreatingANearDuplicate() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        assertEquals(Style.HARDTEK, app.directory.styleNamed("  HARDTEK "))
        assertEquals(Style.DRUM_AND_BASS, Style.fromLabel("DnB"))
        assertEquals(null, app.directory.styleNamed("  "))
        val hardGroove = app.directory.styleNamed("hard groove")!!
        assertEquals("Hard groove", hardGroove.label)

        val entry = app.directory.createSoundsystem("Son Neuf", emptyList(), null, listOf(hardGroove), emptyList())

        assertEquals(listOf(hardGroove), app.directory.soundsystem(entry.id)?.styles)
        assertEquals(hardGroove, app.directory.styleNamed("HARD GROOVE"))
        assertEquals(Style.HARDTEK, app.directory.styleChoices("hardteck", usedOnly = false).first(), "a typo finds the style")
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
        assertEquals("date;departement;titre;artiste;soundsystem;note;commentaire", csv.first())
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
    fun theAppearanceStartsLight() = runTest {
        val app = teknotes()
        assertEquals(Preferences(Appearance.LIGHT), app.settings.preferences().first())
        app.settings.setAppearance(Appearance.DARK)
        assertEquals(Preferences(Appearance.DARK), app.settings.current())
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
    fun aNightSharedWithoutAnAccountGoesBackToPrivateOnUpdate() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TeknotesDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO night (id, date, shared, created_at, updated_at, synced) VALUES ('a', '2026-10-01', 1, 0, 0, 0), ('b', '2026-10-02', 1, 0, 0, 1)", 0)

        TeknotesDatabase.Schema.migrate(driver, 2, TeknotesDatabase.Schema.version)

        val shared = driver.executeQuery(null, "SELECT id FROM night WHERE shared = 1", { cursor ->
            cursor.next()
            app.cash.sqldelight.db.QueryResult.Value(cursor.getString(0))
        }, 0).value
        assertEquals("b", shared)
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
