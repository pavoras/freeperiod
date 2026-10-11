package org.freeperiod.app.support

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.freeperiod.app.data.SettingsStore

/** F-Droid builds keep only free dependencies: no store purchase. */
@Suppress("UNUSED_PARAMETER")
fun createSupporter(context: Context, settings: SettingsStore, scope: CoroutineScope): Supporter = NoSupporter
