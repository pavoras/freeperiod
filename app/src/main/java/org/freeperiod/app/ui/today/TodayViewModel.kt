package org.freeperiod.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.PeriodValidationException
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

data class DayMarks(val period: Boolean, val predicted: Boolean, val logged: Boolean, val today: Boolean,
    val higherChance: Boolean = false, val possible: Boolean = false)

data class TodayUiState(
    val today: LocalDate,
    val month: YearMonth = YearMonth.from(today),
    val prediction: PredictionState = PredictionState.NoData,
    val cycleDay: Int? = null,
    val periodEndForToday: LocalDate? = null,
    val periodDay: Int? = null,
    val timeline: CycleTimeline? = null,
    val situation: Situation = Situation(),
    val fertileWindow: ClosedRange<LocalDate>? = null,
    val endSaved: PeriodEndReceipt? = null,
    val startedPeriodId: Long? = null,
    val ongoingPeriodId: Long? = null,
    val days: Map<LocalDate, DayMarks> = emptyMap(),
    val loading: Boolean = true,
    val writing: Boolean = false,
    val error: TodayError? = null,
)

data class PeriodEndReceipt(val before: Period, val after: Period)

data class TodayError(val periodError: PeriodError? = null)

class TodayViewModel(
    private val repository: Repository,
    settings: SettingsStore,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val situation: (() -> Situation)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TodayUiState(clock()))
    val state: StateFlow<TodayUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.periods, repository.dayLogs, repository.domainSettings, settings.settings, repository.situation) {
                    _, _, _, _, _ -> Unit
            }.collect { refresh().join() }
        }
    }

    fun refresh(): Job = viewModelScope.launch {
        try {
            recompute(repository.snapshot())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(loading = false, error = TodayError()) }
        }
    }

    fun onResume(): Job = refresh()

    fun showMonth(month: YearMonth): Job {
        mutableState.update { it.copy(month = month) }
        return refresh()
    }

    fun startPeriodToday(): Job = mutate {
        val today = clock()
        // Ended today or yesterday and bleeding again: that period goes on, instead of a second one right next to it.
        val recent = repository.snapshot().periods
            .filter { period -> period.start <= today && period.end?.let { it >= today.minusDays(1) } == true }.maxByOrNull { it.start }
        val period = recent?.let { repository.updatePeriod(it.copy(end = null)).getOrThrow() }
            ?: repository.addPeriod(today, null).getOrThrow()
        mutableState.update { it.copy(startedPeriodId = period.id, endSaved = null) }
    }

    fun confirmEnd(date: LocalDate): Job = mutate {
        val ongoing = requireNotNull(repository.snapshot().periods.lastOrNull { it.end == null && it.start <= clock() })
        // Reject a stale card after midnight instead of saving the wrong bleeding day.
        require(date == periodEndForTapToday(ongoing, clock()))
        val saved = repository.endPeriod(ongoing.id, date).getOrThrow()
        mutableState.update { it.copy(endSaved = PeriodEndReceipt(ongoing, saved)) }
    }

    fun undoPeriodEnd(receipt: PeriodEndReceipt): Job = mutate {
        val current = repository.snapshot().periods.find { it.id == receipt.after.id }
        require(current == receipt.after)
        repository.updatePeriod(receipt.before).getOrThrow()
        mutableState.update { it.copy(endSaved = null) }
    }

    fun pausePredictions(): Job = mutate {
        repository.updateDomainSettings(repository.snapshot().settings.copy(predictionsPaused = true))
    }

    fun dismissError() { mutableState.update { it.copy(error = null) } }

    private fun mutate(action: suspend () -> Unit): Job = viewModelScope.launch {
        if (mutableState.value.writing) return@launch
        mutableState.update { it.copy(writing = true, error = null) }
        try {
            action()
            recompute(repository.snapshot())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableState.update { it.copy(error = TodayError((failure as? PeriodValidationException)?.error)) }
        } finally {
            mutableState.update { it.copy(writing = false) }
        }
    }

    private fun recompute(data: BackupData) {
        mutableState.update { previous ->
            todayState(data, clock(), previous.month, situation?.invoke() ?: data.situation).copy(writing = previous.writing,
                error = previous.error, endSaved = previous.endSaved, startedPeriodId = previous.startedPeriodId)
        }
    }
}

/** The card, timeline and calendar share one snapshot and one injected calendar date. */
internal fun todayState(data: BackupData, today: LocalDate, month: YearMonth = YearMonth.from(today),
    situation: Situation = data.situation): TodayUiState {
    val periods = data.periods.filter { it.start <= today }.sortedBy { it.start }
    val prediction = predict(periods, PredictionSettings(data.settings.typicalCycleLength, data.settings.predictionsPaused), situation, today)
    val chances = periodChances(prediction)
    val breakDays = (prediction as? PredictionState.ScheduledBreak)?.range
    val window = fertileWindow(prediction, situation)
    val logged = data.dayLogs.filterNot { it.isEmpty() }.map { it.date }.toSet()
    val ongoing = periods.lastOrNull { it.end == null }
    val lengths = PeriodRules.cycles(periods).filter { it.eligible }.takeLast(6).map { it.length }
    val fallback = data.settings.typicalCycleLength ?: lengths.takeIf { it.isNotEmpty() }?.let { Stats.median(it).roundToInt() } ?: 28
    return TodayUiState(today = today, month = month, prediction = prediction,
        cycleDay = PeriodRules.cycleDay(periods, today),
        periodEndForToday = ongoing?.let { periodEndForTapToday(it, today) },
        periodDay = ongoing?.let { Math.toIntExact(DAYS.between(it.start, today) + 1) },
        ongoingPeriodId = ongoing?.id,
        timeline = cycleTimeline(periods, prediction, situation.pill, fallback, today), situation = situation,
        fertileWindow = window,
        days = calendarMonths(YearMonth.from(today)).flatMap { shown -> (1..shown.lengthOfMonth()).map { shown.atDay(it) } }.associate { date ->
            date to DayMarks(PeriodRules.periodOn(periods, date, today) != null,
                breakDays?.contains(date) == true || chances[date] == PeriodChance.LIKELY, date in logged, date == today,
                window?.contains(date) == true, chances[date] == PeriodChance.POSSIBLE)
        }, loading = false)
}
