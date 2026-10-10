package org.freeperiod.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

/** A period in the summary; [days] while ended, [cycleDays] to the next logged start when there is one. */
data class SummaryPeriod(val start: LocalDate, val end: LocalDate?, val days: Int?, val cycleDays: Int?)

/** One summary row: a logged day and/or a period day. */
data class SummaryDay(val date: LocalDate, val periodDay: Boolean, val log: DayLog)

/** Plain facts for the summary of entries: no scores, no flags, nothing interpreted. */
data class Summary(val from: LocalDate, val to: LocalDate, val periods: List<SummaryPeriod>, val days: List<SummaryDay>)

/** Periods touching [from]..[to] and every day in the range that has an entry or is a period day. */
fun summary(periods: List<Period>, logs: List<DayLog>, from: LocalDate, to: LocalDate): Summary {
    val sorted = periods.sortedBy { it.start }
    val rows = sorted.mapIndexedNotNull { index, period ->
        if (period.start > to || (period.end ?: to) < from) return@mapIndexedNotNull null
        SummaryPeriod(period.start, period.end, period.end?.let { DAYS.between(period.start, it).toInt() + 1 },
            sorted.getOrNull(index + 1)?.let { DAYS.between(period.start, it.start).toInt() })
    }
    val periodDays = sorted.flatMap { period ->
        generateSequence(maxOf(period.start, from)) { it.plusDays(1) }.takeWhile { it <= minOf(period.end ?: to, to) }.toList()
    }.toSet()
    val byDate = logs.filter { it.date in from..to && !it.isEmpty() }.associateBy { it.date }
    val days = (periodDays + byDate.keys).sorted().map { SummaryDay(it, it in periodDays, byDate[it] ?: DayLog(it)) }
    return Summary(from, to, rows, days)
}
