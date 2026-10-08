@file:kotlinx.serialization.UseSerializers(LocalDateSerializer::class)

package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import kotlinx.serialization.Serializable

/** Repeating pack rhythm; a zero-day break denotes continuous use. */
@Serializable
data class PillSchedule(val packStart: LocalDate, val activeDays: Int, val breakDays: Int) {
    init {
        require(activeDays in 1..365) { "Active days must be in 1..365" }
        require(breakDays in 0..30) { "Break days must be in 0..30" }
    }

    /** Current one-based pack day, absent before the configured start. */
    fun packDay(today: LocalDate): Int? = if (today < packStart) null
        else (DAYS.between(packStart, today) % (activeDays + breakDays)).toInt() + 1

    /** Current pack boundaries, absent before the configured start. */
    fun packAround(today: LocalDate): ClosedRange<LocalDate>? {
        val day = packDay(today) ?: return null
        val start = today.minusDays(day.toLong() - 1)
        return start..start.plusDays(activeDays.toLong() + breakDays - 1)
    }

    /** Current or upcoming scheduled break within the current pack. */
    fun breakDaysAround(today: LocalDate): ClosedRange<LocalDate>? {
        if (breakDays == 0) return null
        val pack = packAround(today) ?: return null
        return pack.start.plusDays(activeDays.toLong())..pack.endInclusive
    }
}
