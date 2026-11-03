package org.freeperiod.app.rating

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/** Google Play's in-app review card. It talks to the Play Store app on the phone; FreePeriod learns nothing about the rating. */
class PlayRatingPrompt(context: Context) : RatingPrompt {
    private val manager = ReviewManagerFactory.create(context.applicationContext)

    override fun launch(activity: Activity) {
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && !activity.isFinishing) manager.launchReviewFlow(activity, request.result)
        }
    }
}

fun createRatingPrompt(context: Context): RatingPrompt = PlayRatingPrompt(context)
