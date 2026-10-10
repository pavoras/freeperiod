package org.freeperiod.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*

/** Months back from today; 0 means all entries. */
internal val summaryRanges = listOf(3, 6, 12, 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(busy: Boolean, message: Int?, error: Boolean, onCreate: (months: Int, notes: Boolean) -> Unit, onBack: () -> Unit) {
    var months by rememberSaveable { mutableIntStateOf(6) }
    var notes by rememberSaveable { mutableStateOf(false) }
    val t = LocalDaylight.current
    LazyColumn(Modifier.fillMaxSize().background(t.background), contentPadding = PaddingValues(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FpTopBar(stringResource(R.string.summary_screen), onBack) }
        item { Text(stringResource(R.string.summary_intro), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        item {
            SettingsPanel {
                Text(stringResource(R.string.summary_range), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summaryRanges.forEach { option ->
                        FpChip(months == option, { months = option },
                            if (option == 0) stringResource(R.string.summary_all) else pluralStringResource(R.plurals.summary_months, option, option))
                    }
                }
                HorizontalDivider(color = t.line)
                FpSwitchRow(stringResource(R.string.summary_notes), notes, onChange = { notes = it })
                Text(stringResource(R.string.summary_notes_detail), style = MaterialTheme.typography.bodySmall, color = t.muted)
            }
        }
        item { FpButton({ onCreate(months, notes) }, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.summary_create)) } }
        message?.let { item { Text(stringResource(it), color = if (error) MaterialTheme.colorScheme.error else t.ink) } }
        item { Text(stringResource(R.string.summary_footer), style = MaterialTheme.typography.bodySmall, color = t.muted) }
    }
}
