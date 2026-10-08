package org.freeperiod.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SituationTest {
    private fun d(value: String) = LocalDate.parse("2026-$value")
    private val periods = listOf(Period(1, d("03-01"), d("03-05")), Period(2, d("03-29"), d("04-02")))
    private val today = d("04-10")
    private val settings = PredictionSettings(28)

    @Test fun phaseSwitchRestoresPredictions() {
        val regular = predict(periods, settings, today)
        assertEquals(PredictionState.Paused, predict(periods, settings, Situation(phase = LifePhase.PREGNANT), today))
        assertEquals(regular, predict(periods, settings, Situation(), today))
        assertTrue(predict(periods, settings, Situation(phase = LifePhase.PERIMENOPAUSE), today) is PredictionState.Range)
    }

    @Test fun precedenceTable() {
        val pill = PillSchedule(d("04-01"), 21, 7)
        for (paused in listOf(false, true)) for (phase in LifePhase.entries) {
            for (method in Method.entries) for (rhythm in listOf(null, pill, pill.copy(breakDays = 0))) {
                val situation = Situation(phase, method, rhythm, fertileWindowEnabled = true)
                val state = predict(periods, settings.copy(paused = paused), situation, today)
                val expected = when {
                    paused || phase in setOf(LifePhase.PREGNANT, LifePhase.POSTPARTUM) -> PredictionState.Paused::class
                    phase == LifePhase.MENOPAUSE -> PredictionState.Menopause::class
                    method == Method.PILL_COMBINED && rhythm == null -> PredictionState.NeedsPillRhythm::class
                    method == Method.PILL_COMBINED && rhythm!!.breakDays == 0 -> PredictionState.ContinuousPill::class
                    method == Method.PILL_COMBINED -> PredictionState.ScheduledBreak::class
                    else -> PredictionState.Range::class
                }
                assertEquals("$paused/$phase/$method/$rhythm", expected, state::class)
                if (state !is PredictionState.Range) {
                    assertNull(fertileWindow(state, situation))
                    assertFalse(periodReminderDue(state, 2, 2, today, null))
                }
            }
        }
    }

    @Test fun futurePillPackDoesNotFallBackToStatistics() {
        val situation = Situation(method = Method.PILL_COMBINED, pill = PillSchedule(today.plusDays(1), 21, 7))
        assertEquals(PredictionState.NeedsPillRhythm, predict(periods, settings, situation, today))
    }

    @Test fun monthsWithoutPeriodCounts() {
        val completed = listOf(Period(1, d("04-10"), d("04-14")))
        assertEquals(0, fullMonthsSinceLastPeriodEnded(completed, d("05-14")))
        assertEquals(1, fullMonthsSinceLastPeriodEnded(completed, d("05-15")))
        assertEquals(0, fullMonthsSinceLastPeriodEnded(completed + Period(2, d("05-01"), null), d("05-15")))
        assertNull(fullMonthsSinceLastPeriodEnded(emptyList(), today))
        assertEquals(0, fullMonthsSinceLastPeriodEnded(listOf(Period(1, today, null)), today))
        assertNull(fullMonthsSinceLastPeriodEnded(completed, d("04-09")))
        assertEquals(PredictionState.Menopause(1, d("04-14")),
            predict(completed, settings, Situation(phase = LifePhase.MENOPAUSE), d("05-15")))
    }
}
