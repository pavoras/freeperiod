package org.freeperiod.app.rating

import android.app.Activity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import kotlinx.coroutines.flow.first

/** The store's own rating card. Play builds show Google's in-app review (Google decides whether it appears); F-Droid builds have none. */
interface RatingPrompt {
    fun launch(activity: Activity)
}

object NoRatingPrompt : RatingPrompt {
    override fun launch(activity: Activity) {}
}

/** Days of use before the first ask, pause between asks, and how often at most. */
internal const val RATING_FIRST_AFTER_DAYS = 14L
internal const val RATING_PAUSE_DAYS = 120L
internal const val RATING_MAX_ASKS = 3

/**
 * Asks only once FreePeriod has been part of someone's routine for a while: two weeks after the first entry, with
 * 30 logged days or two finished periods, at most three times and four months apart.
 */
internal fun ratingDue(firstUse: LocalDate?, lastAsked: LocalDate?, asks: Int, loggedDays: Int, completedPeriods: Int,
    today: LocalDate): Boolean = firstUse != null && ChronoUnit.DAYS.between(firstUse, today) >= RATING_FIRST_AFTER_DAYS &&
    (loggedDays >= 30 || completedPeriods >= 2) && asks < RATING_MAX_ASKS &&
    (lastAsked == null || ChronoUnit.DAYS.between(lastAsked, today) >= RATING_PAUSE_DAYS)

/** The one moment we ask: right after the user closes a day they logged something on – never on start or mid-task. */
class RatingMoments(private val settings: SettingsStore, private val repository: Repository, private val prompt: RatingPrompt,
    private val clock: () -> LocalDate) {
    suspend fun afterDayLogged(activity: Activity?, date: LocalDate) {
        if (prompt === NoRatingPrompt || activity == null) return
        val today = clock()
        val data = repository.snapshot()
        if (data.dayLogs.none { it.date == date && !it.isEmpty() }) return
        val device = settings.settings.first()
        if (device.firstUse == null) { settings.update { it.copy(firstUse = today) }; return }
        val logged = data.dayLogs.count { !it.isEmpty() && it.date <= today }
        val completed = data.periods.count { it.end != null && it.end!! < today }
        if (!ratingDue(device.firstUse, device.ratingAsked, device.ratingAsks, logged, completed, today)) return
        // Counted before showing, so a store hiccup never turns into asking again and again.
        settings.update { it.copy(ratingAsked = today, ratingAsks = it.ratingAsks + 1) }
        if (!activity.isFinishing) prompt.launch(activity)
    }
}
