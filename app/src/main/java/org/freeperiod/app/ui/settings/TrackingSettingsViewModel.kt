package org.freeperiod.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.ItemSetCategory
import org.freeperiod.app.data.PAIN_DIARY
import org.freeperiod.app.data.Repository
import org.freeperiod.app.ui.day.EntryCategory
import org.freeperiod.app.ui.day.builtInCategories
import org.freeperiod.app.ui.day.dayEntryState
import org.freeperiod.app.ui.day.entryCategories
import org.freeperiod.app.ui.day.hiddenByDefault
import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData
import org.freeperiod.engine.backup.BackupSettings

private const val PAIN_DIARY_DAILY = "${PAIN_DIARY}daily"

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
            // The ovulation test comes with trying to conceive and goes with it, also after the order was changed.
            val trying = LifePhase.TRYING_TO_CONCEIVE
            if (value.phase != previous.phase && trying in setOf(value.phase, previous.phase))
                repository.snapshot().overrides.find { it.key == "category:ovulation_test" }
                    ?.let { repository.setUiOverride(it.copy(hidden = value.phase != trying)) }
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
    fun reorder(keys: List<String>) = write { writeOrder(keys) }

    private suspend fun writeOrder(keys: List<String>) {
        val data = repository.snapshot()
        // A category that starts hidden (medication) stays hidden when only its place changes.
        keys.forEachIndexed { index, key -> repository.setUiOverride((data.overrides.find { it.key == key }
            ?: UiOverride(key, builtInCategories.find { "category:${it.key}" == key }?.hiddenByDefault(data.situation) == true, index))
            .copy(sortOrder = index)) }
    }
    /** Adds the category when its id is 0, otherwise saves the edited one. */
    fun category(draft: CustomCategory) = write {
        if (draft.id == 0L) repository.addCustomCategory(draft.name, draft.iconKey, sortOrder = 100 + mutable.value.data.customCategories.size,
            singleChoice = draft.singleChoice, counted = draft.counted)
        else repository.updateCustomCategory(draft)
    }
    fun archive(category: CustomCategory) = write { repository.updateCustomCategory(category.copy(archived = true)) }
    fun restore(category: CustomCategory) = write { repository.updateCustomCategory(category.copy(archived = false)) }
    fun deleteCategory(category: CustomCategory) = write { repository.deleteCustomCategory(category.id) }
    fun restoreItem(tag: Tag) = write { repository.updateTag(tag.copy(archived = false)) }
    fun deleteItemForever(tag: Tag) = write { repository.deleteItemsForever(setOf(tag.id)) }
    fun resetCustomization() = write { repository.resetCustomization() }

    /**
     * Shows or hides a built-in day-entry category. Medication keeps its place; appetite and sex drive join in their
     * usual place when turned on (after symptoms / after sex), also after the order was changed.
     */
    fun showCategory(field: String, visible: Boolean) = write {
        val data = repository.snapshot()
        val default = builtInCategories.first { it.key == field }
        val key = "category:$field"
        val previous = data.overrides.find { it.key == key }
        val hiddenByDefault = default.hiddenByDefault(data.situation)
        repository.setUiOverride((previous ?: UiOverride(key, hiddenByDefault, default.order)).copy(hidden = !visible))
        if (visible && (previous?.hidden ?: hiddenByDefault)) when (field) {
            "appetite" -> placeAfter(key, listOfNotNull(dailyKey(data), "category:symptoms"))
            "libido" -> placeAfter(key, listOf("category:sex"))
        }
    }

    private fun dailyKey(data: BackupData) = data.customCategories.find { it.itemSet == PAIN_DIARY_DAILY }?.let { "customCategory:${it.id}" }

    /** Moves [key] right after the first of [anchors] that is shown; no shown anchor leaves the order as it is. */
    private suspend fun placeAfter(key: String, anchors: List<String>) {
        val epoch = LocalDate.ofEpochDay(0)
        val categories = entryCategories(dayEntryState(repository.snapshot(), epoch, epoch), includeHidden = true)
        val anchor = anchors.firstOrNull { anchor -> categories.any { it.overrideKey == anchor && !it.hidden } } ?: return
        val rest = categories.map { it.overrideKey } - key
        val at = rest.indexOf(anchor) + 1
        writeOrder(rest.take(at) + key + rest.drop(at))
    }

    /** Switches the History chart of a category on or off. */
    fun chart(category: EntryCategory, on: Boolean) = write {
        val previous = repository.snapshot().overrides.find { it.key == category.overrideKey }
            ?: UiOverride(category.overrideKey, category.hidden, category.order)
        repository.setUiOverride(previous.copy(chart = on))
    }

    fun painDiary(on: Boolean, categories: List<ItemSetCategory>, archive: Boolean = false) = write {
        if (!on) return@write repository.turnOffPainDiary(archive)
        val data = repository.snapshot()
        // Categories that start off (effect on your day) keep whatever the user chose.
        val keys = listOf("category:pain", "category:medication") + data.customCategories
            .filter { category -> categories.any { it.key == category.itemSet && !it.hidden } }.map { "customCategory:${it.id}" }
        val show = keys.mapNotNull { key -> data.overrides.find { it.key == key }?.copy(hidden = false)
            ?: builtInCategories.find { "category:${it.key}" == key && it.defaultHidden }?.let { UiOverride(key, false, it.order) } }
        repository.turnOnPainDiary(categories, show)
        // Pain, medication and where/during stand together, where pain was; effect on your day follows symptoms.
        val after = repository.snapshot()
        val ordered = entryCategories(dayEntryState(after, LocalDate.ofEpochDay(0), LocalDate.ofEpochDay(0)), includeHidden = true).map { it.overrideKey }
        val group = listOf("category:pain", "category:medication") + categories.filter { it.key != PAIN_DIARY_DAILY }.mapNotNull { set ->
            after.customCategories.find { it.itemSet == set.key }?.let { "customCategory:${it.id}" } }
        val rest = ordered.filterNot { it in group }
        val at = ordered.takeWhile { it != "category:pain" }.count { it !in group }
        writeOrder(rest.take(at) + group + rest.drop(at))
        dailyKey(after)?.let { placeAfter(it, listOf("category:symptoms")) }
    }
    fun item(name: String, icon: String, categoryId: Long?, field: String?, categoryName: String) = write {
        if (field != null) repository.addBuiltInItem(field, name, icon, categoryName)
        else repository.addTag(name, categoryId, icon)
    }
}
