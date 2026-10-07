package org.freeperiod.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.R
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.app.data.AppSettings
import org.freeperiod.app.data.LockTimeout
import java.time.LocalTime
import org.freeperiod.engine.backup.*

data class SettingsUiState(
    val typicalCycleLength: Int? = null,
    val predictionsPaused: Boolean = false,
    val dynamicColor: Boolean = false,
    val loading: Boolean = true,
    val writing: Boolean = false,
    val message: Int? = null,
    val device: AppSettings = AppSettings(),
    val lockCanEnable: Boolean = false,
)

class SettingsViewModel(private val repository: Repository, private val settings: SettingsStore,
    private val canLock: () -> Boolean = { false }) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state = mutableState.asStateFlow()
    private var pending: Job? = null

    init {
        viewModelScope.launch {
            combine(repository.domainSettings, settings.settings) { domain, device ->
                SettingsUiState(domain.typicalCycleLength, domain.predictionsPaused, device.dynamicColor, loading = false,
                    device = device, lockCanEnable = canLock())
            }.catch { mutableState.update { it.copy(loading = false, message = R.string.error_storage) } }
                .collect { value -> mutableState.update { value.copy(writing = it.writing, message = it.message) } }
        }
    }

    fun setTypicalLength(length: Int?): Job = enqueue {
        require(length == null || length in 15..90)
        repository.updateDomainSettings(repository.snapshot().settings.copy(typicalCycleLength = length))
    }

    fun setPaused(paused: Boolean): Job = enqueue {
        repository.updateDomainSettings(repository.snapshot().settings.copy(predictionsPaused = paused))
    }

    fun setDynamicColor(enabled: Boolean): Job = enqueue { settings.update { it.copy(dynamicColor = enabled) } }
    fun setPeriodReminder(enabled: Boolean): Job = enqueue { settings.update { it.copy(periodReminder = enabled) } }
    fun setReminderDays(days: Int): Job = enqueue {
        require(days in 1..5)
        settings.update { it.copy(periodReminderDaysBefore = days) }
    }
    fun setDailyReminder(enabled: Boolean): Job = enqueue { settings.update { it.copy(dailyReminder = enabled) } }
    fun setReminderTime(time: LocalTime): Job = enqueue { settings.update { it.copy(dailyReminderTime = time) } }
    fun setExplicitNotifications(enabled: Boolean): Job = enqueue { settings.update { it.copy(explicitNotifications = enabled) } }
    fun setLockEnabled(enabled: Boolean): Job = enqueue {
        if (enabled && !canLock()) {
            mutableState.update { it.copy(message = R.string.lock_unavailable) }
        } else settings.update { it.copy(lockEnabled = enabled) }
    }
    fun setLockTimeout(timeout: LockTimeout): Job = enqueue { settings.update { it.copy(lockTimeout = timeout) } }

    fun deleteAllData(): Job = enqueue {
        repository.replaceAll(BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(),
            settings = BackupSettings(null, false)))
        mutableState.update { it.copy(message = R.string.data_deleted) }
    }

    private fun enqueue(action: suspend () -> Unit): Job {
        val previous = pending
        return viewModelScope.launch {
            previous?.join()
            mutableState.update { it.copy(writing = true, message = null) }
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(message = R.string.error_storage) } }
            finally { mutableState.update { it.copy(writing = false) } }
        }.also { pending = it }
    }
}
