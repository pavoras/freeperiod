package org.freeperiod.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*
import org.freeperiod.engine.importing.CsvDateFormat

@Composable
fun ImportScreen(state: ImportUiState, onCsv: () -> Unit, onBackup: () -> Unit,
    onFormat: (CsvDateFormat) -> Unit, onConfirm: () -> Unit, onBack: () -> Unit) {
    val tones = LocalDaylight.current
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
    Column(Modifier.fillMaxSize().background(tones.background).verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(FpSpacing.section)) {
        FpTopBar(stringResource(R.string.import_title), onBack)
        Text(stringResource(R.string.import_intro))
        FpButton(onCsv, Modifier.fillMaxWidth(), enabled = !state.busy, primary = false) { Text(stringResource(R.string.import_csv)) }
        FpButton(onBackup, Modifier.fillMaxWidth(), enabled = !state.busy, primary = false) { Text(stringResource(R.string.import_backup)) }
        Text(stringResource(R.string.import_coming_soon), style = MaterialTheme.typography.bodySmall, color = tones.muted)
        if (state.formats.isNotEmpty()) {
            Text(stringResource(R.string.import_choose_format), style = MaterialTheme.typography.titleMedium)
            state.formats.forEach { format ->
                FpChip(false, { onFormat(format) }, stringResource(when (format) {
                    CsvDateFormat.ISO -> R.string.import_format_iso
                    CsvDateFormat.DAY_MONTH_YEAR -> R.string.import_format_dmy
                    CsvDateFormat.MONTH_DAY_YEAR -> R.string.import_format_mdy
                }), Modifier.fillMaxWidth(), enabled = !state.busy)
            }
        }
        if (state.periods.isNotEmpty()) {
            val first = state.periods.minOf { it.start }
            val last = state.periods.maxOf { it.end ?: it.start }
            SettingsPanel {
                Text(pluralStringResource(R.plurals.import_preview, state.periods.size, state.periods.size, first.format(dates), last.format(dates)), style = MaterialTheme.typography.titleMedium)
                if (state.periods.any { it.end == null }) Text(stringResource(R.string.import_ongoing))
                if (state.imported == null) FpButton(onConfirm, enabled = !state.busy) { Text(stringResource(R.string.import_confirm)) }
                else Text(listOfNotNull(pluralStringResource(R.plurals.import_imported, state.imported, state.imported),
                    if (state.skipped > 0) pluralStringResource(R.plurals.import_skipped, state.skipped, state.skipped) else null).joinToString(" "))
            }
        }
        if (state.busy) CircularProgressIndicator()
        state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}
