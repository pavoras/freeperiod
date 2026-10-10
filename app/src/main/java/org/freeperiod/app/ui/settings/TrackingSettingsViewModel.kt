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
    fun override(key: String, hidden: Boolean, order: Int) = write {
        val previous = repository.snapshot().overrides.find { it.key == key } ?: UiOverride(key, false, order)
        repository.setUiOverride(previous.copy(hidden = hidden, sortOrder = order))
    }
    fun appearance(value: UiOverride) = write { repository.setUiOverride(value) }
    fun editItem(tag: Tag) = write { repository.updateTag(tag) }
    fun deleteItem(id: Long) = write { repository.deleteItem(id) }
    fun reorder(keys: List<String>) = write {
        val overrides = repository.snapshot().overrides
        keys.forEachIndexed { index, key -> repository.setUiOverride(
            (overrides.find { it.key == key } ?: UiOverride(key, false, index)).copy(sortOrder = index)) }
    }
    fun category(name: String, icon: String, existing: CustomCategory? = null, singleChoice: Boolean = existing?.singleChoice ?: false) = write {
        if (existing == null) repository.addCustomCategory(name, icon, sortOrder = 100 + mutable.value.data.customCategories.size, singleChoice = singleChoice)
        else repository.updateCustomCategory(existing.copy(name = name, iconKey = icon, singleChoice = singleChoice))
    }
    fun archive(category: CustomCategory) = write { repository.updateCustomCategory(category.copy(archived = true)) }
    fun restore(category: CustomCategory) = write { repository.updateCustomCategory(category.copy(archived = false)) }
    fun item(name: String, icon: String, categoryId: Long?, field: String?, categoryName: String) = write {
        if (field != null) repository.addBuiltInItem(field, name, icon, categoryName)
        else repository.addTag(name, categoryId, icon)
    }
}
