package org.freeperiod.app.ui.day

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.freeperiod.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodEndPicker(start: LocalDate, today: LocalDate, onDismiss: () -> Unit, onStart: (LocalDate?) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return date in start..today
            }
            override fun isSelectableYear(year: Int): Boolean = year in start.year..today.year
        })
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            picker.selectedDateMillis?.let { onStart(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
        }) { Text(stringResource(R.string.confirm)) }
    }, dismissButton = {
        Row {
            TextButton(onClick = { onStart(null) }) { Text(stringResource(R.string.period_still_ongoing)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    }) {
        DatePicker(picker, title = { Text(stringResource(R.string.period_end_date)) }, showModeToggle = true)
    }
}
