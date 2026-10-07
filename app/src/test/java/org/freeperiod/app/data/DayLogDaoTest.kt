package org.freeperiod.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayLogDaoTest : DatabaseTest() {
    @Test fun datesRoundTripAsEpochDay() = runBlocking {
        val log = DayLog(today, FlowLevel.NONE, Mood.GOOD, setOf(Symptom.CRAVINGS, Symptom.HEADACHE),
            Pain.MILD, Sex.NONE, Discharge.CREAMY, "Test note")
        repository.saveDayLog(log)
        assertEquals(today.toEpochDay(), db.dayLogDao().getAll().single().epochDay)
        assertEquals(log, repository.dayLogs.first()[today])
    }

    @Test fun emptyDayLogDeletesRow() = runBlocking {
        val tag = repository.addTag("Exercise")
        repository.saveDayLog(DayLog(today, flow = FlowLevel.NONE, tagIds = setOf(tag.id)))
        repository.saveDayLog(DayLog(today))
        assertTrue(db.dayLogDao().getAll().isEmpty())
        assertTrue(db.dayLogDao().getLinks().isEmpty())
    }

    @Test fun tagsJoinedPerDay() = runBlocking {
        val a = repository.addTag("Exercise")
        val b = repository.addTag("Travel")
        repository.saveDayLog(DayLog(today, tagIds = setOf(a.id, b.id)))
        repository.saveDayLog(DayLog(today.minusDays(1), tagIds = setOf(b.id)))
        val logs = repository.dayLogs.first()
        assertEquals(setOf(a.id, b.id), logs[today]?.tagIds)
        assertEquals(setOf(b.id), logs[today.minusDays(1)]?.tagIds)
        repository.saveDayLog(DayLog(today, tagIds = setOf(a.id)))
        assertEquals(setOf(a.id), repository.dayLogs.first()[today]?.tagIds)
    }
}
