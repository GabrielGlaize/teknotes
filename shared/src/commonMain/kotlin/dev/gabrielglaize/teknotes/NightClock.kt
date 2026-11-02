package dev.gabrielglaize.teknotes

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/** A night starts in the evening and ends in the morning. */
object NightClock {
    /** Before this hour, "I'm here" belongs to the night that started the day before. */
    const val NIGHT_ENDS_AT_HOUR = 12

    /** ISO date of the night that [now] belongs to. */
    fun nightDate(now: LocalDateTime): String {
        val date = if (now.hour < NIGHT_ENDS_AT_HOUR) now.date.minus(DatePeriod(days = 1)) else now.date
        return date.toString()
    }
}
