package org.freeperiod.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.Repository
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

data class HistoryUiState(
    val cycles: List<Cycle> = emptyList(),
    val cycleLength: Double? = null,
    val periodLength: Double? = null,
    val eligibleCycles: Int = 0,
    val completedPeriods: Int = 0,
    val symptomCounts: Map<Symptom, Int> = emptyMap(),
    val symptomCycles: Int = 0,
    val loading: Boolean = true,
    val writing: Boolean = false,
    val error: Boolean = false,
)

internal fun historyState(data: BackupData, today: LocalDate): HistoryUiState {
    val periods = data.periods.filter { it.start <= today }
    val cycles = PeriodRules.cycles(periods)
    val eligible = cycles.filter { it.eligible }
    val lengths = periods.mapNotNull { period ->
        period.end?.let { Math.toIntExact(ChronoUnit.DAYS.between(period.start, it) + 1) }
    }
    val window = cycles.takeLast(3)
    return HistoryUiState(cycles = cycles.asReversed(),
        cycleLength = eligible.takeIf { it.isNotEmpty() }?.let { Stats.median(it.map { cycle -> cycle.length }) },
        periodLength = lengths.takeIf { it.isNotEmpty() }?.let(Stats::median),
        eligibleCycles = eligible.size, completedPeriods = lengths.size,
        symptomCounts = if (window.isEmpty()) emptyMap() else Stats.symptomCounts(data.dayLogs, window.first().start, window.last().nextStart),
        symptomCycles = window.size, loading = false)
}

class HistoryViewModel(private val repository: Repository, private val clock: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()
    private var pending: Job? = null

    init {
        viewModelScope.launch {
            combine(repository.periods, repository.dayLogs) { _, _ -> Unit }.collect { refresh().join() }
        }
    }

    fun refresh(): Job = enqueue { reload() }
    fun onResume(): Job = refresh()

    fun setCycleIncluded(id: Long, included: Boolean): Job = enqueue {
        mutableState.update { it.copy(writing = true, error = false) }
        val period = requireNotNull(repository.snapshot().periods.find { it.id == id })
        repository.updatePeriod(period.copy(cycleUse = if (included) CycleUse.INCLUDE else CycleUse.EXCLUDE)).getOrThrow()
        reload()
    }

    private suspend fun reload() { mutableState.value = historyState(repository.snapshot(), clock()) }

    private fun enqueue(action: suspend () -> Unit): Job {
        val previous = pending
        return viewModelScope.launch {
            previous?.join()
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            } finally {
                mutableState.update { it.copy(writing = false) }
            }
        }.also { pending = it }
    }
}
