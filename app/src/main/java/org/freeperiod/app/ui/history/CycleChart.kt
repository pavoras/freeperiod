package org.freeperiod.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.*
import org.freeperiod.engine.*

@Composable
internal fun CycleChart(cycles: List<Cycle>, onSelect: (Long) -> Unit, onVisible: (Set<Long>) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = cycles.lastIndex.coerceAtLeast(0))
    val latestVisible by rememberUpdatedState(onVisible)
    val tones = LocalDaylight.current
    val locale = LocalConfiguration.current.locales[0]
    val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val shortDate = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
    val max = cycles.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    LaunchedEffect(cycles.lastOrNull()?.startPeriodId) { if (cycles.isNotEmpty()) list.scrollToItem(cycles.lastIndex) }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }.toSet() }
            .distinctUntilChanged().collect { latestVisible(it) }
    }
    val olderHidden by remember { derivedStateOf { list.canScrollBackward } }
    Box(Modifier.fillMaxWidth().height(252.dp)) {
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(FpSpacing.gap), modifier = Modifier.fillMaxSize().testTag("cycle-chart")) {
            items(cycles, key = { it.startPeriodId }) { cycle ->
                val reason = cycle.ineligibleReason?.let { stringResource(reasonLabel(it)) } ?: stringResource(R.string.history_included)
                val periodLength = cycle.periodLength?.let { pluralStringResource(R.plurals.history_period_days, it, it) } ?: stringResource(R.string.history_need_period)
                val description = stringResource(R.string.history_bar_description, cycle.start.format(fullDate),
                    cycle.nextStart.minusDays(1).format(fullDate), cycle.length, periodLength, reason)
                Column(Modifier.width(68.dp).fillMaxHeight().testTag("history-bar-${cycle.startPeriodId}")
                    .clickable(role = Role.Button) { onSelect(cycle.startPeriodId) }
                    .semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cycle.length.toString(), style = MaterialTheme.typography.labelLarge, color = tones.ink)
                        Box(Modifier.padding(top = FpSpacing.compact).width(38.dp).height((168f * cycle.length / max).coerceAtLeast(12f).dp)
                            .clip(FpShapes.bar).background(if (cycle.eligible) tones.accent.accent else tones.accent.container)) {
                            if (!cycle.eligible) Canvas(Modifier.fillMaxSize()) {
                                val step = 8.dp.toPx()
                                var x = -size.height
                                while (x < size.width) {
                                    drawLine(tones.muted, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                                    x += step
                                }
                            }
                        }
                    }
                    Text(cycle.start.format(shortDate), Modifier.padding(top = FpSpacing.gap, bottom = FpSpacing.gap),
                        style = MaterialTheme.typography.labelSmall, color = tones.muted)
                }
            }
        }
        if (olderHidden) Box(Modifier.align(Alignment.CenterStart).width(24.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(tones.background, tones.background.copy(alpha = 0f)))))
    }
}

internal fun reasonLabel(reason: IneligibleReason): Int = when (reason) {
    IneligibleReason.EXCLUDED_BY_USER -> R.string.history_excluded
    IneligibleReason.TOO_SHORT -> R.string.history_too_short
    IneligibleReason.TOO_LONG -> R.string.history_too_long
}
