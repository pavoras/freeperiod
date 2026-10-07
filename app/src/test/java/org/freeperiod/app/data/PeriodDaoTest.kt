package org.freeperiod.app.data

import kotlinx.coroutines.runBlocking
import org.freeperiod.app.data.db.PeriodEntity
import org.freeperiod.engine.CycleUse
import org.freeperiod.engine.PeriodError
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeriodDaoTest : DatabaseTest() {
    @Test fun datesRoundTripAsEpochDay() = runBlocking {
        val start = today.minusDays(10)
        val end = start.plusDays(4)
        val id = db.periodDao().insert(PeriodEntity(0, start.toEpochDay(), end.toEpochDay(), CycleUse.EXCLUDE))
        val saved = db.periodDao().getAll().single()
        assertEquals(id, saved.id)
        assertEquals(start.toEpochDay(), saved.startEpochDay)
        assertEquals(end.toEpochDay(), saved.endEpochDay)
        assertEquals(CycleUse.EXCLUDE, saved.cycleUse)
    }

    @Test fun startPeriodRejectsOverlap() = runBlocking {
        repository.addPeriod(today.minusDays(5), null).getOrThrow()
        val failure = repository.addPeriod(today, null).exceptionOrNull()
        assertTrue(failure is PeriodValidationException)
        assertEquals(PeriodError.OVERLAP, (failure as PeriodValidationException).error)
        assertEquals(1, db.periodDao().getAll().size)
    }
}
