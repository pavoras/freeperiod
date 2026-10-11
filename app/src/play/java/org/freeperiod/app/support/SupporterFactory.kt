package org.freeperiod.app.support

import android.content.Context

fun createSupporter(context: Context): Supporter = PlaySupporter(context.applicationContext)
