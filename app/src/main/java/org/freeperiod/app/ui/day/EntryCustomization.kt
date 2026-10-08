package org.freeperiod.app.ui.day

import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpIcons
import org.freeperiod.engine.*

data class EntryCategory(val key: String, val label: Int, val icon: Int, val order: Int,
    val category: CustomCategory? = null, val hidden: Boolean = false) {
    val overrideKey get() = category?.let { "customCategory:${it.id}" } ?: "category:$key"
}

internal val builtInCategories = listOf(
    EntryCategory("mood", R.string.entry_mood, R.drawable.ic_mood_okay, 0),
    EntryCategory("flow", R.string.entry_flow, R.drawable.ic_fp_cycle, 1),
    EntryCategory("pain", R.string.entry_pain, R.drawable.ic_symptom_cramps, 2),
    EntryCategory("symptoms", R.string.entry_symptoms, R.drawable.ic_symptom_fatigue, 3),
    EntryCategory("sex", R.string.entry_sex, R.drawable.ic_fp_sex, 4),
    EntryCategory("discharge", R.string.entry_discharge, R.drawable.ic_fp_discharge, 5),
    EntryCategory("tags", R.string.entry_tags, R.drawable.ic_fp_tags, 6),
    EntryCategory("note", R.string.entry_note, R.drawable.ic_fp_note, 7),
    EntryCategory("ovulation_test", R.string.entry_ovulation_test, R.drawable.ic_fp_today, 8),
)

internal fun entryCategories(state: DayEntryUiState, includeHidden: Boolean = false): List<EntryCategory> {
    val categories = builtInCategories + state.customCategories.filter { !it.archived && it.iconKey != "builtin:symptoms" }.map {
        EntryCategory("custom:${it.id}", R.string.entry_tags, FpIcons.byKey[it.iconKey] ?: R.drawable.ic_fp_tags, it.sortOrder, it)
    }
    return categories.map { category ->
        val override = state.overrides.find { it.key == category.overrideKey }
        category.copy(order = override?.sortOrder ?: category.order,
            hidden = override?.hidden ?: false)
    }.filter { includeHidden || (!it.hidden && (it.key != "ovulation_test" || state.situation.phase == LifePhase.TRYING_TO_CONCEIVE)) }
        .sortedWith(compareBy({ it.order }, { it.key }))
}
internal fun DayEntryUiState.itemVisible(field: String, name: String): Boolean = overrides.none { it.key == "item:$field:$name" && it.hidden }
internal fun DayEntryUiState.visibleTags(category: EntryCategory): List<Tag> {
    val categoryId = if (category.key == "symptoms") customCategories.find { it.iconKey == "builtin:symptoms" }?.id ?: return emptyList()
        else category.category?.id
    return tags.filter { it.categoryId == categoryId && (!it.archived || it.id in log.tagIds) && overrides.none { o -> o.key == "tag:${it.id}" && o.hidden } }
        .sortedBy { tag -> overrides.find { it.key == "tag:${tag.id}" }?.sortOrder ?: 0 }
}
internal val menopauseSymptoms = setOf(Symptom.HOT_FLUSHES, Symptom.NIGHT_SWEATS, Symptom.BRAIN_FOG, Symptom.JOINT_PAIN)
internal fun DayEntryUiState.visibleSymptoms(more: Boolean): List<Symptom> {
    val phaseItems = if (situation.phase in setOf(LifePhase.PERIMENOPAUSE, LifePhase.MENOPAUSE)) menopauseSymptoms else emptySet()
    val base = if (more) Symptom.entries.filter { it !in menopauseSymptoms || it in phaseItems || it in log.symptoms }
        else listOf(Symptom.CRAMPS, Symptom.BLOATING, Symptom.HEADACHE) + phaseItems + log.symptoms
    return base.distinct().filter { itemVisible("symptoms", it.name) }
        .sortedBy { symptom -> overrides.find { it.key == "item:symptoms:${symptom.name}" }?.sortOrder ?: base.indexOf(symptom) }
}
