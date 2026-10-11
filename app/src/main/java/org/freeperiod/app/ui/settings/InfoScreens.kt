package org.freeperiod.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import org.freeperiod.app.R
import org.freeperiod.app.support.SupportState
import org.freeperiod.app.ui.components.*
import org.freeperiod.app.ui.theme.*

object AppLinks {
    const val PRIVACY_URL = "https://freeperiod.org/privacy"
    const val SOURCE_URL = "https://github.com/pavoras/freeperiod"
}

@Composable
fun PrivacyScreen(onBack: () -> Unit, onPolicyLink: () -> Unit, purchases: Boolean = false) {
    Column(Modifier.fillMaxSize().background(LocalDaylight.current.background).verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsPageHeader(R.string.privacy_policy, onBack)
        FpCard { MedicalDisclaimer(Modifier.padding(16.dp)) }
        FpCard { Text(stringResource(R.string.privacy_storage), Modifier.padding(16.dp)) }
        Text(stringResource(R.string.privacy_offline))
        if (purchases) Text(stringResource(R.string.privacy_purchases))
        Text(stringResource(R.string.privacy_files))
        Text(stringResource(R.string.privacy_delete))
        Text(stringResource(R.string.privacy_contact))
        TextButton(onClick = onPolicyLink) { Text(stringResource(R.string.privacy_web_link)) }
    }
}

@Composable
fun AboutScreen(version: String, onBack: () -> Unit, onSourceLink: () -> Unit, storeLibrary: Int? = null) {
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
        storeLibrary?.let { Text(stringResource(it)) }
    }
}

/** The optional one-time support purchase (Play builds only); it unlocks nothing. */
@Composable
fun SupportScreen(support: SupportState, onBack: () -> Unit, onBuy: (String) -> Unit) {
    val t = LocalDaylight.current
    Column(Modifier.fillMaxSize().background(t.background).verticalScroll(rememberScrollState()).padding(FpSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsPageHeader(R.string.support_title, onBack)
        if (support.supported) FpCard {
            // Never split the name across lines ("Free-Period").
            Text(stringResource(R.string.support_thanks), Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.None))
        }
        Text(stringResource(R.string.support_body))
        if (support.pending) Text(stringResource(R.string.support_pending), color = t.muted)
        // Supporting again stays one big tap away (owner, 2026-10-11).
        if (!support.pending) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            support.products.forEach { product ->
                FpButton({ onBuy(product.id) }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.support_button, product.price), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(stringResource(R.string.support_privacy), style = MaterialTheme.typography.bodySmall, color = t.muted)
    }
}

@Composable
internal fun SettingsPageHeader(title: Int, onBack: () -> Unit) {
    FpTopBar(stringResource(title), onBack)
}
