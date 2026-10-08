package org.freeperiod.app.lock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*

@Composable
fun LockedScreen(onUnlock: () -> Unit, message: Int? = null) {
    Surface(Modifier.fillMaxSize(), color = LocalDaylight.current.background) {
        Column(Modifier.fillMaxSize().padding(FpSpacing.screen), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.locked_message))
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            FpButton(onClick = onUnlock) { Text(stringResource(R.string.unlock)) }
        }
    }
}
