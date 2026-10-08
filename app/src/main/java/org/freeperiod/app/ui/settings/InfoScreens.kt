package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*

object AppLinks {
    const val PRIVACY_URL = "https://freeperiod.org/privacy"
    const val SOURCE_URL = "https://github.com/freeperiod/freeperiod"
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onPolicyLink: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalDaylight.current.background).verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsPageHeader(R.string.privacy_policy, onBack)
        FpCard { MedicalDisclaimer(Modifier.padding(16.dp)) }
        FpCard { Text(stringResource(R.string.privacy_storage), Modifier.padding(16.dp)) }
        Text(stringResource(R.string.privacy_offline))
        Text(stringResource(R.string.privacy_files))
        Text(stringResource(R.string.privacy_delete))
        Text(stringResource(R.string.privacy_contact))
        TextButton(onClick = onPolicyLink) { Text(stringResource(R.string.privacy_web_link)) }
    }
}

@Composable
fun AboutScreen(version: String, onBack: () -> Unit, onSourceLink: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LocalDaylight.current.background).verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsPageHeader(R.string.about, onBack)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.about_version, version))
        FpCard { Text(stringResource(R.string.about_license), Modifier.padding(16.dp)) }
        TextButton(onClick = onSourceLink) { Text(stringResource(R.string.about_source)) }
        MedicalDisclaimer()
        Text(stringResource(R.string.about_open_source), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.about_libraries))
    }
}

@Composable
internal fun SettingsPageHeader(title: Int, onBack: () -> Unit) {
    FpTopBar(stringResource(title), onBack)
}
