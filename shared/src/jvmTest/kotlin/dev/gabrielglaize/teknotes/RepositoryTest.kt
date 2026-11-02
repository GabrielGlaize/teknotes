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
import kotlin.test.assertTrue

class RepositoryTest {
    private var now = LocalDateTime(2026, 10, 31, 23, 0)

    private fun teknotes(): Teknotes {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TeknotesDatabase.Schema.create(driver)
        return Teknotes(driver) { now.toInstant(TimeZone.currentSystemDefault()) }
    }

    @Test
    fun checkInOpensTheSameNightUntilNoon() = runTest {
        val app = teknotes()
        val evening = app.notebook.checkIn("59")
        now = LocalDateTime(2026, 11, 1, 4, 30)
        val lateNight = app.notebook.checkIn()
        now = LocalDateTime(2026, 11, 1, 22, 0)
        val nextEvening = app.notebook.checkIn()

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
        val night = app.notebook.checkIn()
        val hit = app.directory.search("tekno krat").first()
        val entryId = app.notebook.addEntry(night.id, hit)
        app.notebook.rateEntry(entryId, 5, "  Montée de malade  ")

        val entries = app.notebook.entries(night.id).first()
        assertEquals("Teknokrates", entries.single().label)
        assertEquals(5, entries.single().rating)
        assertEquals("Montée de malade", entries.single().comment)
        assertEquals(1, app.notebook.nights().first().single().entryCount)
        assertFailsWith<IllegalArgumentException> { app.notebook.rateEntry(entryId, 6, "") }
    }

    @Test
    fun deletingANightDeletesItsEntries() = runTest {
        val app = teknotes()
        app.directory.seedDemoIfEmpty()
        val night = app.notebook.checkIn()
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
        val soundsystem = app.directory.createSoundsystem("Kick Familial", department = "13", styles = listOf(Style.TRIBE))
        val artist = app.directory.createArtist("Dj Galet", soundsystemIds = listOf(soundsystem.id))

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
        app.notebook.createNight("2026-09-12")

        val json = app.notebook.exportJson()
        assertTrue("\"Lunatek\"" in json && "29 · Finistère" in json && "2026-09-12" in json)
        val csv = app.notebook.exportCsv().lines()
        assertEquals("date;departement;titre;nom;type;note;commentaire", csv.first())
        assertEquals(3, csv.size)
    }
}
