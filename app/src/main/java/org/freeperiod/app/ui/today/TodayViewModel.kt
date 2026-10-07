package org.freeperiod.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.PeriodValidationException
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

data class DayMarks(val period: Boolean, val predicted: Boolean, val logged: Boolean, val today: Boolean)

data class TodayUiState(
    val today: LocalDate,
    val month: YearMonth = YearMonth.from(today),
    val prediction: PredictionState = PredictionState.NoData,
    val cycleDay: Int? = null,
    val endQuestion: LocalDate? = null,
    val ongoingPeriodId: Long? = null,
    val days: Map<LocalDate, DayMarks> = emptyMap(),
    val loading: Boolean = true,
    val writing: Boolean = false,
    val error: TodayError? = null,
)

data class TodayError(val periodError: PeriodError? = null)

class TodayViewModel(
    private val repository: Repository,
    settings: SettingsStore,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(TodayUiState(clock()))
    val state: StateFlow<TodayUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.periods, repository.dayLogs, repository.domainSettings, settings.settings) {
                    _, _, _, _ -> Unit
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

    fun startPeriodToday(): Job = mutate { repository.addPeriod(clock(), null).getOrThrow() }

    fun confirmEnd(date: LocalDate): Job = mutate {
        // Read the current ongoing period inside the repository snapshot, rather than
        // relying on a potentially stale card after another mutation.
        val ongoing = repository.snapshot().periods.lastOrNull { it.end == null }
        requireNotNull(ongoing)
        repository.endPeriod(ongoing.id, date).getOrThrow()
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
        val today = clock()
        val prediction = predict(data.periods,
            PredictionSettings(data.settings.typicalCycleLength, data.settings.predictionsPaused), today)
        val predicted = predictedDays(prediction)
        val logged = data.dayLogs.map { it.date }.toSet()
        mutableState.update { previous ->
            val month = previous.month
            val days = (1..month.lengthOfMonth()).associate { number ->
                val date = month.atDay(number)
                date to DayMarks(PeriodRules.periodOn(data.periods, date, today) != null,
                    predicted?.contains(date) == true, date in logged, date == today)
            }
            previous.copy(today = today, prediction = prediction,
                cycleDay = PeriodRules.cycleDay(data.periods, today),
                endQuestion = PeriodRules.endQuestion(data.periods, today),
                ongoingPeriodId = data.periods.lastOrNull { it.end == null && it.start <= today }?.id,
                days = days, loading = false)
        }
    }
}
