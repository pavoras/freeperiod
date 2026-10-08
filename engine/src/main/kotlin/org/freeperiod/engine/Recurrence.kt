@file:kotlinx.serialization.UseSerializers(LocalDateSerializer::class)

package org.freeperiod.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.TemporalAdjusters.nextOrSame
import kotlinx.serialization.Serializable

/** Calendar recurrence, independent of local notification time and time zone. */
@Serializable
sealed interface Recurrence {
    @Serializable data object Daily : Recurrence
    @Serializable data class EveryNDays(val n: Int, val anchor: LocalDate) : Recurrence {
        init { require(n in 1..999) { "Day interval must be in 1..999" } }
    }
    @Serializable data class Weekly(val day: DayOfWeek) : Recurrence
    @Serializable data class MonthlyOnDay(val day: Int) : Recurrence {
        init { require(day in 1..31) { "Month day must be in 1..31" } }
    }
    @Serializable data class EveryNMonths(val n: Int, val anchor: LocalDate) : Recurrence {
        init { require(n in 1..999) { "Month interval must be in 1..999" } }
    }
    @Serializable data class Once(val date: LocalDate) : Recurrence
}

/** First occurrence at/after the threshold; month intervals always derive from the anchor. */
fun Recurrence.nextDate(after: LocalDate, inclusive: Boolean): LocalDate? {
    val threshold = if (inclusive) after else after.plusDays(1)
    return when (this) {
        Recurrence.Daily -> threshold
        is Recurrence.EveryNDays -> {
            if (threshold <= anchor) anchor else {
                val elapsed = DAYS.between(anchor, threshold)
                anchor.plusDays(((elapsed + n - 1) / n) * n)
            }
        }
        is Recurrence.Weekly -> threshold.with(nextOrSame(day))
        is Recurrence.MonthlyOnDay -> {
            val month = YearMonth.from(threshold)
            val candidate = month.atDay(minOf(day, month.lengthOfMonth()))
            if (candidate >= threshold) candidate else month.plusMonths(1).let { it.atDay(minOf(day, it.lengthOfMonth())) }
        }
        is Recurrence.EveryNMonths -> {
            if (threshold <= anchor) anchor else {
                val k = MONTHS.between(YearMonth.from(anchor), YearMonth.from(threshold)) / n
                val candidate = anchor.plusMonths(k * n)
                if (candidate >= threshold) candidate else anchor.plusMonths((k + 1) * n)
            }
        }
        is Recurrence.Once -> date.takeIf { it >= threshold }
    }
}
