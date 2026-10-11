package org.freeperiod.app.support

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.freeperiod.app.data.SettingsStore

fun createSupporter(context: Context, settings: SettingsStore, scope: CoroutineScope): Supporter =
    PlaySupporter(context.applicationContext, settings, scope)
