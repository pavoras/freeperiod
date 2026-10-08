package org.freeperiod.app.data

import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageV2Test : DatabaseTest() {
    private fun v2() = BackupData(
        periods = listOf(Period(7, today.minusDays(20), today.minusDays(16), CycleUse.EXCLUDE)),
        dayLogs = listOf(DayLog(today, flow = FlowLevel.NONE, symptoms = setOf(Symptom.HOT_FLUSHES),
            note = "x".repeat(2100), tagIds = setOf(9), ovulationTest = OvulationTest.NEGATIVE)),
        tags = listOf(Tag(9, "Walk", true, 4, "leaf")), settings = BackupSettings(29, true),
        situation = Situation(LifePhase.PERIMENOPAUSE, Method.PILL_COMBINED, PillSchedule(today, 21, 7)),
        customCategories = listOf(CustomCategory(4, "Activities", "leaf", 2, true)),
        overrides = listOf(UiOverride("customCategory:4", true, 1), UiOverride("tag:9", true, 3)),
        reminders = listOf(Reminder(12, ReminderKind.CUSTOM, "Check", Recurrence.EveryNMonths(3, today), LocalTime.of(9, 15), true)),
        hintDismissals = setOf(7),
    )

    @Test fun snapshotReplaceRoundTripV2() = runBlocking {
        val data = v2()
        repository.replaceAll(data)
        assertEquals(data, repository.snapshot())
        repository.replaceAll(data.copy(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            customCategories = emptyList(), overrides = emptyList(), reminders = emptyList(), hintDismissals = emptySet()))
        repository.replaceAll(data)
        assertEquals(data, repository.snapshot())
    }

    @Test fun addPeriodsSkipsOverlaps() = runBlocking {
        repository.addPeriod(today.minusDays(20), today.minusDays(16)).getOrThrow()
        val overlap = Period(0, today.minusDays(18), today.minusDays(14))
        val first = Period(0, today.minusDays(10), today.minusDays(6), CycleUse.INCLUDE)
        val secondOverlap = Period(0, today.minusDays(8), today.minusDays(5))
        val second = Period(0, today, null)
        assertEquals(listOf(overlap, secondOverlap), repository.addPeriods(listOf(overlap, first, secondOverlap, second)))
        assertEquals(3, repository.snapshot().periods.size)
        assertEquals(CycleUse.INCLUDE, repository.snapshot().periods[1].cycleUse)
    }

    @Test fun invalidBatchRollsBackAllInsertions() = runBlocking {
        assertTrue(runCatching { repository.addPeriods(listOf(Period(0, today.minusDays(10), today.minusDays(6)),
            Period(0, today.plusDays(1), null))) }.isFailure)
        assertTrue(repository.snapshot().periods.isEmpty())
    }

    @Test fun restoreResetsDeliveryIds() = runBlocking {
        val data = v2()
        repository.replaceAll(data)
        repository.markReminderDelivered(12, today)
        assertEquals(today.toEpochDay(), db.reminderDao().getAll().single().lastDeliveredDate)
        var reconciled = false
        val restoring = Repository(db, clock = { today }, afterRestore = {
            assertNull(db.reminderDao().getAll().single().lastDeliveredDate)
            assertEquals(data, repository.snapshot())
            reconciled = true
        })
        restoring.replaceAll(data)
        assertTrue(reconciled)
        assertTrue(db.reminderDao().getAll().single().enabled)
    }

    @Test fun hiddenItemsKeptInHistory() = runBlocking {
        repository.replaceAll(v2())
        assertEquals(setOf(9L), repository.dayLogs.first()[today]!!.tagIds)
        assertEquals("Walk", repository.tags.first().single().name)
        assertTrue(repository.overrides.first().all { it.hidden })
        assertTrue(repository.customCategories.first().single().archived)
    }

    @Test fun sameItemNameAllowedInDifferentCategories() = runBlocking {
        val category = repository.addCustomCategory("Activities", "leaf")
        repository.addTag("Walk")
        repository.addTag("Walk", category.id, "leaf")
        assertTrue(runCatching { repository.addTag("walk") }.isFailure)
        assertEquals(2, repository.snapshot().tags.size)
    }

    @Test fun v2WritesAndFlowsPreserveContracts() = runBlocking {
        val category = repository.addCustomCategory("Activities", "leaf")
        repository.updateCustomCategory(category.copy(sortOrder = 4))
        val tag = repository.addTag("Walk", category.id, "leaf")
        repository.updateTag(tag.copy(archived = true))
        repository.setUiOverride(UiOverride("tag:${tag.id}", true, 3))
        repository.updateSituation(Situation(phase = LifePhase.POSTPARTUM))
        val reminder = repository.saveReminder(Reminder(0, ReminderKind.DAILY_LOG, null, Recurrence.Daily, LocalTime.NOON, false))
        val period = repository.addPeriod(today.minusDays(5), today.minusDays(1)).getOrThrow()
        repository.dismissLongCycleHint(period.id)
        assertEquals(LifePhase.POSTPARTUM, repository.situation.first().phase)
        assertEquals(4, repository.customCategories.first().single().sortOrder)
        assertEquals(reminder, repository.reminders.first().single())
        assertEquals(setOf(period.id), repository.hintDismissals.first())
        repository.deletePeriod(period.id)
        assertTrue(repository.hintDismissals.first().isEmpty())
    }

    @Test fun allReminderRecurrencesRoundTrip() = runBlocking {
        val rules = listOf(Recurrence.Daily, Recurrence.EveryNDays(3, today),
            Recurrence.Weekly(java.time.DayOfWeek.MONDAY), Recurrence.MonthlyOnDay(31),
            Recurrence.EveryNMonths(3, today), Recurrence.Once(today.plusDays(30)))
        val reminders = rules.map { rule ->
            repository.saveReminder(Reminder(0, ReminderKind.CUSTOM, null, rule, LocalTime.of(9, 15), false))
        }
        assertEquals(reminders, repository.snapshot().reminders)
        val data = repository.snapshot()
        repository.replaceAll(data)
        assertEquals(data, repository.snapshot())
        assertEquals("MONDAY", db.reminderDao().getAll()[2].weekday)
    }
}
