package org.freeperiod.app.ui.today

import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*

internal fun todayFixture(scenario: String, today: LocalDate = LocalDate.of(2026, 4, 12)): TodayUiState {
    val latest = when (scenario) {
        "ongoing" -> LocalDate.of(2026, 4, 10)
        "rangePassed" -> today.minusDays(35)
        "menopause" -> LocalDate.of(2025, 9, 1)
        else -> LocalDate.of(2026, 4, 1)
    }
    val periods = if (scenario == "empty") emptyList() else (0..3).map { index ->
        val start = latest.minusDays((3 - index) * 28L)
        Period(index + 1L, start, if (scenario == "ongoing" && index == 3) null else start.plusDays(4))
    }
    val situation = when (scenario) {
        "scheduledBreak" -> Situation(method = Method.PILL_COMBINED, pill = PillSchedule(LocalDate.of(2026, 4, 1), 21, 7))
        "menopause" -> Situation(phase = LifePhase.MENOPAUSE)
        else -> Situation()
    }
    val data = BackupData(periods = periods, dayLogs = if (scenario == "empty") emptyList() else listOf(DayLog(today, mood = Mood.GOOD)),
        tags = emptyList(), settings = BackupSettings(null, false))
    return todayState(data, today, situation = situation)
}
