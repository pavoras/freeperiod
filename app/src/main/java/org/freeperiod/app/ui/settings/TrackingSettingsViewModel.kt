package org.freeperiod.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.Repository
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData
import org.freeperiod.engine.backup.BackupSettings

data class TrackingSettingsState(val data: BackupData = BackupData(periods = emptyList(), dayLogs = emptyList(), tags = emptyList(), settings = BackupSettings(null, false)), val loading: Boolean = true, val error: Boolean = false)

class TrackingSettingsViewModel(private val repository: Repository) : ViewModel() {
    private val mutable = MutableStateFlow(TrackingSettingsState())
    val state = mutable.asStateFlow()
    private var pending: Job? = null
    init {
        viewModelScope.launch {
            merge(repository.situation.map { Unit }, repository.reminders.map { Unit }, repository.tags.map { Unit },
                repository.customCategories.map { Unit }, repository.overrides.map { Unit }).collect {
                mutable.value = TrackingSettingsState(repository.snapshot(), loading = false)
            }
        }
    }
    private fun write(action: suspend () -> Unit): Job {
        val previous = pending
        return viewModelScope.launch {
            previous?.join()
            try { action(); mutable.value = TrackingSettingsState(repository.snapshot(), loading = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(error = true) } }
        }.also { pending = it }
    }
    fun situation(value: Situation): Job {
        val previous = mutable.value.data.situation
        return write {
            val current = repository.snapshot().situation
            repository.updateSituation(current.copy(
                phase = if (value.phase != previous.phase) value.phase else current.phase,
                method = if (value.method != previous.method) value.method else current.method,
                pill = if (value.pill != previous.pill) value.pill else current.pill,
                fertileWindowEnabled = if (value.fertileWindowEnabled != previous.fertileWindowEnabled) value.fertileWindowEnabled
                    else current.fertileWindowEnabled))
        }
    }
    fun reminder(value: Reminder) = write { repository.saveReminder(value) }
    fun deleteReminder(id: Long) = write { repository.deleteReminder(id) }
    fun override(key: String, hidden: Boolean, order: Int) = write { repository.setUiOverride(UiOverride(key, hidden, order)) }
    fun reorder(keys: List<String>) = write {
        keys.forEachIndexed { index, key -> repository.setUiOverride(UiOverride(key,
            mutable.value.data.overrides.find { it.key == key }?.hidden ?: false, index)) }
    }
    fun category(name: String, icon: String, existing: CustomCategory? = null) = write {
        if (existing == null) repository.addCustomCategory(name, icon, sortOrder = 100 + mutable.value.data.customCategories.size)
        else repository.updateCustomCategory(existing.copy(name = name, iconKey = icon))
    }
    fun archive(category: CustomCategory) = write { repository.updateCustomCategory(category.copy(archived = true)) }
    fun item(name: String, icon: String, categoryId: Long?, symptoms: Boolean, categoryName: String) = write {
        val categories = repository.snapshot().customCategories
        var backingName = categoryName
        var suffix = 2
        while (categories.any { it.name.equals(backingName, ignoreCase = true) }) backingName = "$categoryName ${suffix++}"
        val id = if (symptoms) categories.find { it.iconKey == "builtin:symptoms" }?.id
            ?: repository.addCustomCategory(backingName, "builtin:symptoms").id else categoryId
        repository.addTag(name, id, icon)
    }
}
