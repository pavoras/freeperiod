package org.freeperiod.app.rating

import android.app.Activity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RatingPromptTest : DatabaseTest() {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private val shown = mutableListOf<Activity>()
    private val prompt = object : RatingPrompt { override fun launch(activity: Activity) { shown += activity } }

    @Before fun openStore() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temporary.root, "settings.preferences_pb") })
    }
    @After fun closeStore() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    @Test fun dueOnlyAfterRealUseAndRarely() {
        val start = today.minusDays(RATING_FIRST_AFTER_DAYS)
        assertTrue(ratingDue(start, null, 0, 30, 0, today))
        assertTrue(ratingDue(start, null, 0, 3, 2, today))
        // Too new, too little logged, asked recently or often enough: no.
        assertFalse(ratingDue(null, null, 0, 30, 2, today))
        assertFalse(ratingDue(today.minusDays(13), null, 0, 30, 2, today))
        assertFalse(ratingDue(start, null, 0, 29, 1, today))
        assertFalse(ratingDue(start, today.minusDays(RATING_PAUSE_DAYS - 1), 1, 30, 2, today))
        assertTrue(ratingDue(start, today.minusDays(RATING_PAUSE_DAYS), 1, 30, 2, today))
        assertFalse(ratingDue(start, null, RATING_MAX_ASKS, 30, 2, today))
    }

    @Test fun asksOnceAfterClosingALoggedDay() = runBlocking {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val moments = RatingMoments(settings, repository, prompt) { today }
        (0L until 30L).forEach { repository.saveDayLog(DayLog(today.minusDays(it), mood = Mood.GOOD)) }
        // The first closed day only starts the clock.
        moments.afterDayLogged(activity, today)
        assertEquals(today, settings.settings.first().firstUse)
        assertTrue(shown.isEmpty())
        settings.update { it.copy(firstUse = today.minusDays(20)) }
        // A day without an entry is not a moment.
        moments.afterDayLogged(activity, today.minusDays(40))
        assertTrue(shown.isEmpty())
        moments.afterDayLogged(activity, today)
        assertEquals(listOf(activity), shown)
        assertEquals(1, settings.settings.first().ratingAsks)
        moments.afterDayLogged(activity, today)
        assertEquals(1, shown.size)
    }

    @Test fun fdroidNeverAsks() = runBlocking {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        RatingMoments(settings, repository, NoRatingPrompt) { today }.afterDayLogged(activity, today)
        assertNull(settings.settings.first().firstUse)
    }
}
