package org.freeperiod.app.ui.today

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Day and month: English puts the month first ("Apr 28"), German writes "28. Apr.", Portuguese and Spanish
 * "28 de abr.", the other languages day first ("28 avr.").
 */
internal fun dayMonthPattern(locale: Locale, long: Boolean = false): String {
    val month = if (long) "MMMM" else "MMM"
    return when (locale.language) {
        "en" -> "$month d"
        "de" -> "d. $month"
        "pt", "es" -> "d 'de' $month"
        else -> "d $month"
    }
}

/** The year only for dates outside the current year. */
private fun yearSuffix(date: LocalDate, today: LocalDate, locale: Locale): String = when {
    date.year == today.year -> ""
    locale.language == "en" -> ", yyyy"
    locale.language in setOf("pt", "es") -> " 'de' yyyy"
    else -> " yyyy"
}

/** Compact ranges; years appear only for noncurrent years. */
internal fun formatPredictionRange(start: LocalDate, end: LocalDate, today: LocalDate, locale: Locale): String {
    fun format(date: LocalDate) = date.format(DateTimeFormatter.ofPattern(dayMonthPattern(locale) + yearSuffix(date, today, locale), locale))
    if (YearMonth.from(start) == YearMonth.from(end)) {
        if (locale.language == "en") return "${start.format(DateTimeFormatter.ofPattern("MMM", locale))} ${start.dayOfMonth}–${end.dayOfMonth}" +
            if (start.year == today.year) "" else ", ${start.year}"
        return "${start.dayOfMonth}${if (locale.language == "de") "." else ""}–${format(end)}"
    }
    return "${format(start)} – ${format(end)}"
}
