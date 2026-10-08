package org.freeperiod.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest : DatabaseTest() {
    @Test fun replaceAllIsAtomic() = runBlocking {
        repository.addPeriod(today.minusDays(10), today.minusDays(6)).getOrThrow()
        repository.updateDomainSettings(BackupSettings(29, true))
        val before = repository.snapshot()
        // Force a database failure after deletion and insertion to verify transaction rollback.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_reminder BEFORE INSERT ON reminders BEGIN SELECT RAISE(ABORT, 'test'); END")
        val invalid = BackupData(periods = listOf(Period(10, today.minusDays(2), today)),
            dayLogs = emptyList(), tags = listOf(Tag(1, "Travel")),
            settings = BackupSettings(30, false), reminders = listOf(
                Reminder(1, ReminderKind.CUSTOM, null, Recurrence.Daily, java.time.LocalTime.NOON, true)))
        assertTrue(runCatching { repository.replaceAll(invalid) }.isFailure)
        assertEquals(before, repository.snapshot())
    }

    @Test fun snapshotReplaceRoundTrip() = runBlocking {
        val tag = repository.addTag("Travel")
        val period = repository.addPeriod(today.minusDays(8), today.minusDays(4)).getOrThrow()
        repository.updatePeriod(period.copy(cycleUse = CycleUse.INCLUDE)).getOrThrow()
        repository.saveDayLog(DayLog(today, mood = Mood.OKAY, tagIds = setOf(tag.id)))
        repository.updateDomainSettings(BackupSettings(27, true))
        val before = repository.snapshot()
        repository.replaceAll(BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false)))
        repository.replaceAll(before)
        assertEquals(before, repository.snapshot())
    }

    @Test fun concurrentStartsCreateOnlyOnePeriod() = runBlocking {
        val results = List(8) { async { repository.addPeriod(today, null) } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, repository.snapshot().periods.size)
        results.filter { it.isFailure }.forEach {
            assertEquals(PeriodError.OVERLAP, (it.exceptionOrNull() as PeriodValidationException).error)
        }
    }

    @Test fun failedDayTagWriteKeepsPreviousLog() = runBlocking {
        val original = DayLog(today, mood = Mood.GOOD)
        repository.saveDayLog(original)
        assertTrue(runCatching { repository.saveDayLog(original.copy(tagIds = setOf(999))) }.isFailure)
        assertEquals(listOf(original), repository.snapshot().dayLogs)
    }

    @Test fun updatePeriodRejectsOverlapAndKeepsDates() = runBlocking {
        val a = repository.addPeriod(today.minusDays(30), today.minusDays(26)).getOrThrow()
        repository.addPeriod(today.minusDays(2), today).getOrThrow()
        val result = repository.updatePeriod(a.copy(end = today))
        assertEquals(PeriodError.OVERLAP, (result.exceptionOrNull() as PeriodValidationException).error)
        assertEquals(a, repository.snapshot().periods.first())
    }
}
