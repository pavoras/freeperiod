package org.freeperiod.app.ui.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.SettingsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayViewModelTest : DatabaseTest() {
    @Test fun windowUsesThePersistedPreferenceAndSituation() = runTest {
        repository.addPeriod(today.minusDays(10), today.minusDays(6)).getOrThrow()
        repository.updateDomainSettings(org.freeperiod.engine.backup.BackupSettings(28, false))
        val vm = viewModel()
        vm.refresh().join()
        assertNotNull(vm.state.value.fertileWindow)
        repository.updateSituation(org.freeperiod.engine.Situation(fertileWindowEnabled = false))
        vm.refresh().join()
        assertNull(vm.state.value.fertileWindow)
        assertTrue(vm.state.value.days.values.none { it.higherChance })
        repository.updateSituation(org.freeperiod.engine.Situation(phase = org.freeperiod.engine.LifePhase.PREGNANT))
        vm.refresh().join()
        assertNull(vm.state.value.fertileWindow)
        repository.updateSituation(org.freeperiod.engine.Situation())
        vm.refresh().join()
        assertNotNull(vm.state.value.fertileWindow)
    }

    @Test fun theChosenViewIsRememberedAndJumpsCount() = runTest {
        val vm = viewModel()
        vm.refresh().join()
        vm.showRing(true).join()
        assertTrue(vm.state.value.ring)
        val again = viewModel()
        again.refresh().join()
        assertTrue(again.state.value.ring)
        again.jumpToToday()
        again.refresh().join()
        assertEquals(1, again.state.value.todayJump)
        again.showRing(false).join()
        assertFalse(again.state.value.ring)
    }

    @Test fun longPressThenTapEntersAPastPeriod() = runTest {
        val vm = viewModel()
        vm.refresh().join()
        vm.longPressDay(today.plusDays(1)).join()
        assertNull(vm.state.value.pick)
        vm.longPressDay(today.minusDays(20)).join()
        assertEquals(RangePick(today.minusDays(20)), vm.state.value.pick)
        vm.pickDay(today.minusDays(16)).join()
        val saved = repository.snapshot().periods.single()
        assertEquals(today.minusDays(20), saved.start)
        assertEquals(today.minusDays(16), saved.end)
        assertNull(vm.state.value.pick)
    }

    @Test fun stillGoingOnlyForARecentStartWithNoLaterPeriod() = runTest {
        repository.addPeriod(today.minusDays(5), today.minusDays(3)).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        vm.longPressDay(today.minusDays(8)).join()
        assertEquals(RangePick(today.minusDays(8)), vm.state.value.pick)
        vm.pickSingle(ongoing = true).join()
        assertEquals(1, repository.snapshot().periods.size)
        vm.longPressDay(today.minusDays(1)).join()
        assertEquals(RangePick(today.minusDays(1), canContinue = true), vm.state.value.pick)
        vm.longPressDay(today.minusDays(20)).join()
        assertEquals(false, vm.state.value.pick?.canContinue)
    }

    @Test fun longPressInsideASavedPeriodPicksItsLastDay() = runTest {
        repository.addPeriod(today.minusDays(10), today.minusDays(8)).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        vm.longPressDay(today.minusDays(9)).join()
        val id = repository.snapshot().periods.single().id
        assertEquals(RangePick(today.minusDays(10), id), vm.state.value.pick)
        vm.pickDay(today.minusDays(6)).join()
        assertEquals(today.minusDays(6), repository.snapshot().periods.single().end)
    }

    @Test fun savedNoticeIsShownOnlyOnce() = runTest {
        repository.addPeriod(today.minusDays(2), null).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        vm.confirmEnd(today.minusDays(1)).join()
        assertFalse(vm.state.value.endSavedShown)
        vm.endSavedShown()
        vm.refresh().join()
        assertNotNull(vm.state.value.endSaved)
        assertTrue(vm.state.value.endSavedShown)
    }

    @Test fun theFoldedCardIsRemembered() = runTest {
        val vm = viewModel()
        vm.refresh().join()
        vm.foldCard(true).join()
        val again = viewModel()
        again.refresh().join()
        assertTrue(again.state.value.cardFolded)
    }

    @Test fun anUnusuallyLongPeriodAsksOnceIfItIsOver() = runTest {
        listOf(90L, 62L, 34L).forEach { repository.addPeriod(today.minusDays(it), today.minusDays(it - 4)).getOrThrow() }
        repository.addPeriod(today.minusDays(13), null).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        assertEquals(HomeQuestion.PERIOD_OVER, vm.state.value.question)
        vm.answer(HomeQuestion.PERIOD_OVER, false).join()
        assertNull(vm.state.value.question)
        val again = viewModel()
        again.refresh().join()
        assertNull(again.state.value.question)
    }

    @Test fun cancellingThePickBringsTheQuestionBack() = runTest {
        listOf(90L, 62L, 34L).forEach { repository.addPeriod(today.minusDays(it), today.minusDays(it - 4)).getOrThrow() }
        repository.addPeriod(today.minusDays(13), null).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        vm.answer(HomeQuestion.PERIOD_OVER, true).join()
        assertNull(vm.state.value.question)
        vm.cancelPick()
        vm.refresh().join()
        assertEquals(HomeQuestion.PERIOD_OVER, vm.state.value.question)
    }

    @Test fun yesToPeriodOverPicksItsLastDay() = runTest {
        listOf(90L, 62L, 34L).forEach { repository.addPeriod(today.minusDays(it), today.minusDays(it - 4)).getOrThrow() }
        repository.addPeriod(today.minusDays(13), null).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        vm.answer(HomeQuestion.PERIOD_OVER, true).join()
        assertEquals(today.minusDays(13), vm.state.value.pick?.start)
        vm.pickDay(today.minusDays(8)).join()
        assertEquals(today.minusDays(8), repository.snapshot().periods.single { it.start == today.minusDays(13) }.end)
    }

    @Test fun todayUsesStoredSituation() = runTest {
        repository.updateSituation(org.freeperiod.engine.Situation(phase = org.freeperiod.engine.LifePhase.MENOPAUSE))
        val vm = viewModel()
        vm.refresh().join()
        assertTrue(vm.state.value.prediction is org.freeperiod.engine.PredictionState.Menopause)
    }
    private val models = ViewModelStore()
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { models.clear(); Dispatchers.resetMain() }

    private fun viewModel(clock: () -> java.time.LocalDate = { today }) = TodayViewModel(repository,
        SettingsStore(ApplicationProvider.getApplicationContext<Context>()), clock).also { models.put("today", it) }

    @Test fun calendarMarksLikelyAndPossiblePeriodDaysForTheNextTwoPeriods() {
        val days = todayFixture("regular").days
        fun day(month: Int, day: Int) = java.time.LocalDate.of(2026, month, day)
        assertEquals((29..30).map { day(4, it) } + (1..3).map { day(5, it) } + (27..31).map { day(5, it) },
            days.filterValues { it.predicted }.keys.sorted())
        assertEquals(listOf(day(4, 28), day(5, 4), day(5, 26), day(6, 1)), days.filterValues { it.possible }.keys.sorted())
    }

    @Test fun todayRecomputesOnResume() = runTest {
        repository.addPeriod(today.minusDays(10), today.minusDays(6)).getOrThrow()
        var date = today
        val vm = viewModel { date }
        vm.refresh().join()
        assertEquals(11, vm.state.value.cycleDay)
        date = today.plusDays(1)
        vm.onResume().join()
        assertEquals(12, vm.state.value.cycleDay)
        assertTrue(vm.state.value.days[date]!!.today)
        assertFalse(vm.state.value.days[today]!!.today)
    }

    @Test fun startPeriodTodayCreatesPeriod() = runTest {
        val vm = viewModel()
        vm.startPeriodToday().join()
        assertEquals(today, repository.snapshot().periods.single().start)
        assertNull(repository.snapshot().periods.single().end)
    }

    @Test fun endTodaySavesYesterdayAndUndoReopensTheSamePeriod() = runTest {
        repository.addPeriod(today.minusDays(2), null).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        assertEquals(today.minusDays(1), vm.state.value.periodEndForToday)
        vm.confirmEnd(vm.state.value.periodEndForToday!!).join()
        assertEquals(today.minusDays(1), repository.snapshot().periods.single().end)
        val receipt = requireNotNull(vm.state.value.endSaved)
        vm.undoPeriodEnd(receipt).join()
        assertNull(repository.snapshot().periods.single().end)
        assertNull(vm.state.value.endSaved)
    }

    @Test fun startAfterEndingTodayOrYesterdayContinuesThatPeriod() = runTest {
        val vm = viewModel()
        vm.startPeriodToday().join()
        vm.confirmEnd(today).join()
        vm.startPeriodToday().join()
        assertNull(vm.state.value.error)
        assertEquals(org.freeperiod.engine.Period(repository.snapshot().periods.single().id, today, null), repository.snapshot().periods.single())
        repository.addPeriod(today.minusDays(40), today.minusDays(36)).getOrThrow()
        val earlier = repository.snapshot().periods.single { it.start == today }
        repository.updatePeriod(earlier.copy(start = today.minusDays(3), end = today.minusDays(1))).getOrThrow()
        vm.startPeriodToday().join()
        assertEquals(2, repository.snapshot().periods.size)
        assertNull(repository.snapshot().periods.single { it.start == today.minusDays(3) }.end)
    }

    @Test fun sameDayEndKeepsTheStartDate() = runTest {
        val vm = viewModel()
        vm.startPeriodToday().join()
        assertEquals(today, vm.state.value.periodEndForToday)
        vm.confirmEnd(today).join()
        assertEquals(today, repository.snapshot().periods.single().end)
    }

    @Test fun undoDoesNotOverwriteASubsequentEdit() = runTest {
        repository.addPeriod(today.minusDays(3), null).getOrThrow()
        val vm = viewModel()
        vm.refresh().join()
        vm.confirmEnd(today.minusDays(1)).join()
        val receipt = requireNotNull(vm.state.value.endSaved)
        repository.updatePeriod(receipt.after.copy(end = today.minusDays(2))).getOrThrow()
        vm.undoPeriodEnd(receipt).join()
        assertEquals(today.minusDays(2), repository.snapshot().periods.single().end)
        assertNotNull(vm.state.value.error)
    }

    @Test fun changingMonthPreservesIndependentMarks() = runTest {
        repository.addPeriod(today.minusDays(2), null).getOrThrow()
        repository.saveDayLog(org.freeperiod.engine.DayLog(today, mood = org.freeperiod.engine.Mood.GOOD))
        val vm = viewModel()
        vm.refresh().join()
        val marks = vm.state.value.days[today]!!
        assertTrue(marks.period && marks.logged && marks.today)
        vm.showMonth(YearMonth.of(2026, 3)).join()
        assertTrue(vm.state.value.days[java.time.LocalDate.of(2026, 3, 31)] != null)
        assertTrue(vm.state.value.days[today]!!.period)
        assertEquals(YearMonth.of(2026, 3), vm.state.value.month)
    }

    @Test fun pausePredictionsPersistsDomainSetting() = runTest {
        val vm = viewModel()
        vm.pausePredictions().join()
        assertTrue(repository.snapshot().settings.predictionsPaused)
    }
}
