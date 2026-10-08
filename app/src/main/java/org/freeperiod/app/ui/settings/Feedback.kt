package org.freeperiod.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.theme.LocalDaylight

internal const val SUPPORT_EMAIL = "support@jaysay.it"

/** Technical lines only; never cycle data, entries or notes. The user sees and can edit them in the mail app. */
internal fun feedbackInfo(version: String, sdk: Int, device: String, locale: Locale): String =
    "FreePeriod. $version · Android API $sdk · $device · ${locale.toLanguageTag()}"

/** Hands a prepared email to the user's mail app; FreePeriod. itself sends nothing and needs no internet. */
@Composable
internal fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val t = LocalDaylight.current
    var includeInfo by rememberSaveable { mutableStateOf(false) }
    var noMailApp by rememberSaveable { mutableStateOf(false) }
    val subject = stringResource(R.string.feedback_subject)
    AlertDialog(onDismissRequest = onDismiss, containerColor = t.surface, textContentColor = t.ink,
        title = { Text(stringResource(R.string.feedback)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feedback_text), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().toggleable(includeInfo, role = Role.Checkbox) { includeInfo = it },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(includeInfo, onCheckedChange = null)
                    Text(stringResource(R.string.feedback_include_info), style = MaterialTheme.typography.bodyMedium)
                }
                if (noMailApp) Text(stringResource(R.string.feedback_no_app, SUPPORT_EMAIL), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            TextButton(onClick = {
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                val body = if (includeInfo) "\n\n—\n" + feedbackInfo(version, Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}", Locale.getDefault()) else ""
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                try { context.startActivity(intent); onDismiss() } catch (_: ActivityNotFoundException) { noMailApp = true }
            }) { Text(stringResource(R.string.feedback_open)) }
        })
}
