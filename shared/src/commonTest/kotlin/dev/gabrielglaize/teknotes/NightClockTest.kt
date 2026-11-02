package dev.gabrielglaize.teknotes

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NightClockTest {
    @Test
    fun eveningBelongsToToday() {
        assertEquals("2026-10-31", NightClock.nightDate(LocalDateTime(2026, 10, 31, 23, 30)))
    }

    @Test
    fun earlyMorningBelongsToTheNightBefore() {
        assertEquals("2026-10-31", NightClock.nightDate(LocalDateTime(2026, 11, 1, 5, 0)))
        assertEquals("2026-12-31", NightClock.nightDate(LocalDateTime(2027, 1, 1, 11, 59)))
    }

    @Test
    fun afternoonStartsANewNight() {
        assertEquals("2026-11-01", NightClock.nightDate(LocalDateTime(2026, 11, 1, 12, 0)))
    }

    @Test
    fun departmentsAreTheFrenchOnesPlusAbroad() {
        assertEquals(102, Departments.all.size)
        assertEquals(Departments.all.size, Departments.all.map { it.code }.toSet().size)
        assertEquals("59 · Nord", Departments.byCode("59")?.label)
        assertEquals("Hors de France", Departments.byCode(Department.ABROAD)?.label)
    }
}
