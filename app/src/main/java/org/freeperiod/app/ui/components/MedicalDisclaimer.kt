package org.freeperiod.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R

/** Shared disclaimer; onboarding keeps the essential note visible in the welcome step. */
@Composable
fun MedicalDisclaimer(modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.medical_disclaimer_title), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (compact) R.string.medical_disclaimer_note else R.string.app_estimates),
            style = MaterialTheme.typography.bodyMedium)
    }
}
