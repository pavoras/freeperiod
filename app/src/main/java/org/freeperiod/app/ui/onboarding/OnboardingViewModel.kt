package org.freeperiod.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.R
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.Period

data class OnboardingUiState(
    val page: Int = 0,
    val today: LocalDate,
    val start: LocalDate? = null,
    val hasEnded: Boolean = false,
    val end: LocalDate? = null,
    val typicalLength: String = "",
    val unknown: Boolean = true,
    val periodReminder: Boolean = false,
    val dailyReminder: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null,
) {
    val valid: Boolean get() = (unknown || typicalLength.toIntOrNull()?.let { it in 15..90 } == true) &&
        (start == null || (start <= today && (!hasEnded || (end != null && end in start..today))))
}

class OnboardingViewModel(private val repository: Repository, private val settings: SettingsStore,
    private val clock: () -> LocalDate) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState(today = clock()))
    val state = mutableState.asStateFlow()
    private var createdPeriod: Period? = null
    fun setStart(date: LocalDate?) { mutableState.update { it.copy(start = date, hasEnded = false, end = null) } }
    fun setHasEnded(value: Boolean) { mutableState.update { it.copy(hasEnded = value, end = null) } }
    fun setEnd(date: LocalDate) { mutableState.update { it.copy(end = date) } }
    fun setTypicalLength(value: String) { mutableState.update { it.copy(typicalLength = value) } }
    fun setUnknown(value: Boolean) { mutableState.update { it.copy(unknown = value) } }
    fun setPeriodReminder(value: Boolean) { mutableState.update { it.copy(periodReminder = value) } }
    fun setDailyReminder(value: Boolean) { mutableState.update { it.copy(dailyReminder = value) } }
    fun systemSettingsUnavailable() { mutableState.update { it.copy(message = R.string.settings_external_error) } }
    fun next() { mutableState.update { if (it.page == 0 || it.valid) it.copy(page = (it.page + 1).coerceAtMost(2), message = null) else it } }
    fun back() { mutableState.update { it.copy(page = (it.page - 1).coerceAtLeast(0), message = null) } }
    fun onResume(): Job = viewModelScope.launch { mutableState.update { it.copy(today = clock()) } }
    fun skip(): Job = complete { settings.update { it.copy(onboardingDone = true) } }
    fun finish(): Job = complete {
        val draft = state.value.copy(today = clock())
        if (!draft.valid) { mutableState.update { it.copy(message = R.string.onboarding_data_error) }; return@complete }
        draft.start?.let { start ->
            val end = if (draft.hasEnded) draft.end else null
            createdPeriod = createdPeriod?.let { repository.updatePeriod(it.copy(start = start, end = end)).getOrThrow() }
                ?: repository.addPeriod(start, end).getOrThrow()
        }
        repository.updateDomainSettings(repository.snapshot().settings.copy(typicalCycleLength = if (draft.unknown) null else draft.typicalLength.toInt()))
        settings.update { it.copy(onboardingDone = true, periodReminder = draft.periodReminder, dailyReminder = draft.dailyReminder) }
    }
    private fun complete(action: suspend () -> Unit): Job = viewModelScope.launch {
        if (state.value.busy) return@launch
        mutableState.update { it.copy(busy = true, message = null) }
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(message = R.string.onboarding_save_error) } }
        finally { mutableState.update { it.copy(busy = false) } }
    }
}
