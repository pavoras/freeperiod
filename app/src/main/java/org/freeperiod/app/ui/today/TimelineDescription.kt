package org.freeperiod.app.ui.today

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.engine.BracketKind
import org.freeperiod.engine.CycleTimeline

internal fun timelineDescription(timeline: CycleTimeline, context: Context, locale: Locale, pack: Boolean = false): String {
    fun date(value: LocalDate) = formatSpokenDate(value, timeline.today, locale)
    fun range(value: ClosedRange<LocalDate>): String {
        if (value.start == value.endInclusive) return date(value.start)
        val sameMonth = YearMonth.from(value.start) == YearMonth.from(value.endInclusive)
        val start = if (sameMonth) value.start.format(DateTimeFormatter.ofPattern(if (locale.language == "de") "d." else "d", locale)) else date(value.start)
        return context.getString(if (sameMonth) R.string.timeline_range_same_month else R.string.timeline_range_across_months, start, date(value.endInclusive))
    }
    return buildList {
        add(context.getString(if (pack || timeline.bracketKind == BracketKind.SCHEDULED_BREAK) R.string.timeline_pack_from else R.string.timeline_cycle_from, date(timeline.axisStart)))
        timeline.recordedPeriod?.let { add(context.getString(R.string.timeline_recorded, range(it))) }
        timeline.expectedPeriodRest?.let { add(context.getString(R.string.timeline_expected, date(it.endInclusive))) }
        add(context.getString(R.string.timeline_today, date(timeline.today)))
        timeline.bracket?.let {
            add(context.getString(if (timeline.bracketKind == BracketKind.SCHEDULED_BREAK) R.string.timeline_break else R.string.timeline_next_period, range(it)))
        }
    }.joinToString(" ")
}

internal fun formatSpokenDate(date: LocalDate, today: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern((if (locale.language == "de") "d. MMMM" else "d MMMM") +
        if (date.year == today.year) "" else " yyyy", locale))
