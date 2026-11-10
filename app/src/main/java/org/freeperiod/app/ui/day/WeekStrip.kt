package org.freeperiod.app.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.FpShapes
import org.freeperiod.app.ui.theme.LocalDaylight

/**
 * The week around the open day, in the calendar's own marks: period days as filled dots, today as a ring,
 * a small dot under days with an entry. Tapping a day opens it.
 */
@Composable
internal fun WeekStrip(date: LocalDate, today: LocalDate, entryDays: Set<LocalDate>, periodDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val t = LocalDaylight.current
    val first = date.with(WeekFields.of(locale).dayOfWeek(), 1)
    val spoken = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")).withLocale(locale)
    val periodLabel = stringResource(R.string.legend_period)
    val entryLabel = stringResource(R.string.legend_entry)
    val todayLabel = stringResource(R.string.legend_today)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (0L..6L).map { first.plusDays(it) }.forEach { day ->
            val selected = day == date
            val period = day in periodDays
            val description = listOfNotNull(day.format(spoken), todayLabel.takeIf { day == today },
                periodLabel.takeIf { period }, entryLabel.takeIf { day in entryDays }).joinToString(", ")
            Column(Modifier.weight(1f).clip(FpShapes.chip)
                .background(if (selected) t.accent.selectedChipBackground else Color.Transparent)
                .selectable(selected, enabled = day <= today, onClick = { onSelect(day) })
                .clearAndSetSemantics { contentDescription = description; this.selected = selected; role = Role.Tab }
                .testTag("week-day-$day").padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).trimEnd('.'),
                    style = MaterialTheme.typography.labelSmall, color = if (selected) t.accent.selectedChipText else t.muted, maxLines = 1)
                var circle = Modifier.size(32.dp)
                if (period) circle = circle.background(t.accent.periodFill, CircleShape).border(1.dp, t.accent.periodBorder, CircleShape)
                if (day == today) circle = circle.border(2.dp, t.accent.todayRing, CircleShape)
                Box(circle, contentAlignment = Alignment.Center) {
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            period -> t.accent.onAccent
                            day > today -> t.muted
                            selected -> t.accent.selectedChipText
                            else -> t.ink
                        })
                }
                Box(Modifier.size(4.dp).background(if (day in entryDays) t.ink else Color.Transparent, CircleShape))
            }
        }
    }
}
