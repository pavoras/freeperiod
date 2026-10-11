package org.freeperiod.app.support

import android.content.Context

/** F-Droid builds keep only free dependencies: no store purchase. */
@Suppress("UNUSED_PARAMETER")
fun createSupporter(context: Context): Supporter = NoSupporter
