package org.freeperiod.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpCard
import org.freeperiod.app.ui.day.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.engine.*

/** One logged item on a diary card: its words and how it is drawn (an icon, or the flow and pain glyphs). */
private data class DiaryMark(val label: String, val icon: Int, val flow: FlowLevel? = null, val pain: Pain? = null)

/** The diary as a list: one card per day with an entry, newest first, under month headings. A card opens its day. */
@Composable
internal fun DiaryList(state: HistoryUiState, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier,
    contentTop: Dp = 0.dp) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    val months = remember(state.diary) { state.diary.groupBy { YearMonth.from(it.log.date) } }
    LazyColumn(modifier.fillMaxSize().testTag("diary-list"),
        contentPadding = PaddingValues(start = FpSpacing.screen, end = FpSpacing.screen, top = contentTop, bottom = FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
        if (state.diary.isEmpty()) item { Text(stringResource(R.string.diary_empty), style = MaterialTheme.typography.bodyLarge) }
        months.forEach { (month, days) ->
            item(key = "month:$month") {
                Text(month.format(monthFormat), Modifier.padding(top = if (month == months.keys.first()) 0.dp else FpSpacing.gap),
                    style = MaterialTheme.typography.titleMedium)
            }
            items(days, key = { it.log.date.toEpochDay() }) { day -> DiaryCard(state, day, onOpenDay) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryCard(state: HistoryUiState, day: DiaryDay, onOpenDay: (LocalDate) -> Unit) {
    val tones = LocalDaylight.current
    val locale = LocalConfiguration.current.locales[0]
    val log = day.log
    val entry = DayEntryUiState(log.date, state.today, log, state.tags, loading = false, situation = state.situation,
        customCategories = state.customCategories, overrides = state.overrides, periodDay = day.periodDay)
    val mood = builtInCategories.single { it.key == "mood" }.let { category ->
        entry.entryItems(category).firstOrNull { entry.selected(it) }?.appearance(entry)
    }
    val marks = diaryMarks(entry)
    val date = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale)
    var expanded by rememberSaveable(log.date) { mutableStateOf(false) }
    var overflows by remember(log.note) { mutableStateOf(false) }
    FpCard(Modifier.fillMaxWidth().testTag("diary-day-${log.date}")) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.diary_open_day)) { onOpenDay(log.date) }
            .padding(FpSpacing.section), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The day's mood leads; without one, the flow; otherwise a quiet note mark.
            Box(Modifier.size(44.dp).background(if (mood != null) tones.accent.container else tones.background, CircleShape),
                contentAlignment = Alignment.Center) {
                when {
                    mood != null -> Icon(painterResource(mood.icon), null, Modifier.size(28.dp), tint = tones.accent.onContainer)
                    log.flow != null && log.flow != FlowLevel.NONE -> FlowGlyph(requireNotNull(log.flow))
                    else -> Icon(painterResource(R.drawable.ic_fp_note), null, Modifier.size(22.dp), tint = tones.muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FpSpacing.gap)) {
                    Text(log.date.format(date), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall)
                    if (day.periodDay) Text(stringResource(R.string.diary_period_day),
                        Modifier.background(tones.accent.periodFill.copy(alpha = 0.25f), FpShapes.chip).padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
                mood?.let { Text(it.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                if (marks.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    marks.forEach { mark ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            when {
                                mark.flow != null -> FlowGlyph(mark.flow, Modifier.size(18.dp))
                                mark.pain != null -> PainGlyph(mark.pain, Modifier.size(18.dp))
                                else -> Icon(painterResource(mark.icon), null, Modifier.size(16.dp), tint = tones.muted)
                            }
                            Text(mark.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                log.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
                    if (overflows || expanded) Text(stringResource(if (expanded) R.string.diary_less else R.string.diary_more),
                        Modifier.heightIn(min = FpSpacing.touch).clickable(role = Role.Button) { expanded = !expanded }
                            .wrapContentHeight(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelLarge, color = tones.actionText)
                }
            }
        }
    }
}

/** Everything logged except mood and note, in day-entry order; pick-one rows say their category ("Pain: mild"). */
@Composable
private fun diaryMarks(entry: DayEntryUiState): List<DiaryMark> = entryCategories(entry)
    .filter { it.key != "mood" && it.key != "note" }.flatMap { category ->
        val pickOne = category.key in levelFields || category.category?.singleChoice == true
        val categoryLabel = category.appearance(entry).label
        entry.entryItems(category).filter { entry.selected(it) }.map { item ->
            val appearance = item.appearance(entry)
            val label = item.tag?.let { countedLabel(appearance.label, entry.log.count(it.id)) } ?: appearance.label
            DiaryMark(if (pickOne) stringResource(R.string.diary_level, categoryLabel, label) else label, appearance.icon,
                flow = entry.log.flow.takeIf { category.key == "flow" && item.tag == null },
                pain = entry.log.pain.takeIf { category.key == "pain" && item.tag == null })
        }
    }
