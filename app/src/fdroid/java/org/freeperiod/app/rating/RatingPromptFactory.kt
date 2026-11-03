package org.freeperiod.app.rating

import android.content.Context

/** F-Droid builds keep only free dependencies: no store rating card. */
@Suppress("UNUSED_PARAMETER")
fun createRatingPrompt(context: Context): RatingPrompt = NoRatingPrompt
