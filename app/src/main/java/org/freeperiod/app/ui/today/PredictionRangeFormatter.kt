package org.freeperiod.app.ui.today

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact ranges in the two shipped languages; years appear only for noncurrent years. */
internal fun formatPredictionRange(start: LocalDate, end: LocalDate, today: LocalDate, locale: Locale): String {
    val german = locale.language == "de"
    val month = DateTimeFormatter.ofPattern("MMM", locale)
    if (YearMonth.from(start) == YearMonth.from(end)) {
        val range = if (german) "${start.dayOfMonth}.–${end.dayOfMonth}. ${start.format(month)}"
            else "${start.format(month)} ${start.dayOfMonth}–${end.dayOfMonth}"
        return range + if (start.year == today.year) "" else if (german) " ${start.year}" else ", ${start.year}"
    }
    fun format(date: LocalDate): String {
        val pattern = if (german) "d. MMM" else "MMM d"
        val year = if (date.year == today.year) "" else if (german) " yyyy" else ", yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern + year, locale))
    }
    return "${format(start)} – ${format(end)}"
}
