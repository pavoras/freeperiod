package org.freeperiod.app.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.PeriodValidationException
import org.freeperiod.app.data.Repository
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

enum class DayEntryError { OVERLAP, END_BEFORE_START, START_IN_FUTURE, END_IN_FUTURE, TAG_NAME, STORAGE }

sealed interface DayEntryEvent {
    data class Cleared(val previous: DayLog) : DayEntryEvent
    data object SuggestPeriodStart : DayEntryEvent
}

data class DayEntryUiState(
    val date: LocalDate,
    val today: LocalDate,
    val log: DayLog = DayLog(date),
    val tags: List<Tag> = emptyList(),
    val periodStart: Boolean = false,
    val periodEnd: Boolean = false,
    val canEndPeriod: Boolean = false,
    val loading: Boolean = true,
    val error: DayEntryError? = null,
    val situation: Situation = Situation(),
    val customCategories: List<CustomCategory> = emptyList(),
    val overrides: List<UiOverride> = emptyList(),
) {
    val readOnly: Boolean get() = date > today
}

internal fun dayEntryState(data: BackupData, date: LocalDate, today: LocalDate) = DayEntryUiState(
    date, today, log = data.dayLogs.find { it.date == date } ?: DayLog(date), tags = data.tags,
    periodStart = data.periods.any { it.start == date }, periodEnd = data.periods.any { it.end == date },
    canEndPeriod = data.periods.any { it.start <= date }, loading = false,
    situation = data.situation, customCategories = data.customCategories, overrides = data.overrides,
)

class DayEntryViewModel(
    private val date: LocalDate,
    private val repository: Repository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(DayEntryUiState(date, clock()))
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<DayEntryEvent>(Channel.UNLIMITED)
    val events = eventChannel.receiveAsFlow()
    private var pending: Job? = null

    init {
        viewModelScope.launch {
            merge(repository.periods.map { Unit }, repository.dayLogs.map { Unit }, repository.tags.map { Unit },
                repository.situation.map { Unit }, repository.customCategories.map { Unit }, repository.overrides.map { Unit })
                .collect { refresh().join() }
        }
    }

    fun refresh(): Job = enqueue { reload() }
    fun onResume(): Job = refresh()
    suspend fun awaitWrites() { pending?.join() }
    fun dismissError() { mutableState.update { it.copy(error = null) } }

    fun setFlow(value: FlowLevel?): Job = editLog(suggestStart = true) { it.copy(flow = value) }
    fun setMood(value: Mood?): Job = editLog { it.copy(mood = value) }
    fun setPain(value: Pain?): Job = editLog { it.copy(pain = value) }
    fun setSex(value: Sex?): Job = editLog { it.copy(sex = value) }
    fun setDischarge(value: Discharge?): Job = editLog { it.copy(discharge = value) }
    fun setOvulationTest(value: OvulationTest?): Job = editLog { it.copy(ovulationTest = value) }
    fun setNote(value: String): Job = editLog { it.copy(note = value.takeUnless(String::isEmpty)) }
    fun toggleSymptom(value: Symptom): Job = editLog {
        it.copy(symptoms = if (value in it.symptoms) it.symptoms - value else it.symptoms + value)
    }
    fun toggleTag(id: Long): Job = editLog {
        it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id)
    }

    fun clearDay(): Job = mutate {
        val previous = currentLog()
        repository.saveDayLog(DayLog(date))
        if (!previous.isEmpty()) eventChannel.send(DayEntryEvent.Cleared(previous))
    }

    fun undoClear(previous: DayLog): Job = mutate {
        require(previous.date == date)
        repository.saveDayLog(previous)
    }

    fun startPeriod(end: LocalDate? = null): Job = mutate { repository.addPeriod(date, end).getOrThrow() }

    fun removePeriodStart(): Job = mutate {
        repository.snapshot().periods.find { it.start == date }?.let { repository.deletePeriod(it.id) }
    }

    fun setPeriodEnd(enabled: Boolean): Job = mutate {
        val periods = repository.snapshot().periods
        if (enabled) {
            periods.filter { it.start <= date }.maxByOrNull { it.start }?.let {
                repository.endPeriod(it.id, date).getOrThrow()
            }
        } else {
            periods.find { it.end == date }?.let { repository.updatePeriod(it.copy(end = null)).getOrThrow() }
        }
    }

    fun addTag(name: String): Job = mutate {
        if (validTagName(name)) {
            val tag = repository.addTag(name)
            val log = currentLog()
            repository.saveDayLog(log.copy(tagIds = log.tagIds + tag.id))
        }
    }

    fun renameTag(id: Long, name: String): Job = mutate {
        if (validTagName(name, id)) repository.renameTag(id, name)
    }

    fun archiveTag(id: Long): Job = mutate { repository.archiveTag(id) }

    private suspend fun validTagName(name: String, id: Long? = null): Boolean {
        val valid = name.isNotBlank() && repository.snapshot().tags.none {
            it.id != id && it.name.equals(name.trim(), ignoreCase = true)
        }
        if (!valid) mutableState.update { it.copy(error = DayEntryError.TAG_NAME) }
        return valid
    }

    private fun editLog(suggestStart: Boolean = false, transform: (DayLog) -> DayLog): Job = mutate {
        val data = repository.snapshot()
        val previous = data.dayLogs.find { it.date == date } ?: DayLog(date)
        val log = transform(previous)
        repository.saveDayLog(log)
        if (suggestStart && log.flow != previous.flow && PeriodRules.suggestsPeriodStart(log, data.periods, clock())) {
            eventChannel.send(DayEntryEvent.SuggestPeriodStart)
        }
    }

    private suspend fun currentLog(): DayLog = repository.snapshot().dayLogs.find { it.date == date } ?: DayLog(date)
    private suspend fun reload() {
        val error = mutableState.value.error
        mutableState.value = dayEntryState(repository.snapshot(), date, clock()).copy(error = error)
    }

    private fun mutate(action: suspend () -> Unit): Job = enqueue {
        if (date > clock()) return@enqueue
        mutableState.update { it.copy(error = null) }
        action()
        reload()
    }

    // Each tap keeps its own save, ordered before subsequent taps and observer refreshes.
    // Transformations read the persisted log so queued edits cannot overwrite newer fields.
    private fun enqueue(action: suspend () -> Unit): Job {
        val previous = pending
        return viewModelScope.launch {
            previous?.join()
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val error = when ((failure as? PeriodValidationException)?.error) {
                    PeriodError.OVERLAP -> DayEntryError.OVERLAP
                    PeriodError.END_BEFORE_START -> DayEntryError.END_BEFORE_START
                    PeriodError.START_IN_FUTURE -> DayEntryError.START_IN_FUTURE
                    PeriodError.END_IN_FUTURE -> DayEntryError.END_IN_FUTURE
                    null -> DayEntryError.STORAGE
                }
                mutableState.update { it.copy(loading = false, error = error) }
            }
        }.also { pending = it }
    }
}
