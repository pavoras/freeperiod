package org.freeperiod.app.ui.day

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpIcons
import org.freeperiod.engine.*

data class EntryCategory(val key: String, val label: Int, val icon: Int, val order: Int,
    val category: CustomCategory? = null, val hidden: Boolean = false, val defaultHidden: Boolean = false) {
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
    // Off until the user turns it on (Customize day entry or the pain diary in My situation).
    EntryCategory("medication", R.string.entry_medication, R.drawable.ic_item_pill, 9, defaultHidden = true),
)

internal data class EntryItem(val key: String, val name: String?, val label: Int, val icon: Int,
    val tag: Tag? = null, val order: Int = 0, val hidden: Boolean = false)
internal data class ResolvedEntryAppearance(val label: String, val icon: Int, val iconKey: String)

/** Android defaults feed the same pure resolver used for built-ins and own rows. */
@Composable
internal fun entryAppearance(key: String, label: Int, icon: Int, overrides: List<UiOverride>,
    tag: Tag? = null, category: CustomCategory? = null): ResolvedEntryAppearance {
    val defaultIconKey = FpIcons.byKey.entries.find { it.value == icon }?.key ?: "default:$icon"
    val resolved = resolveEntryAppearance(key, category?.name ?: stringResource(label),
        category?.iconKey ?: defaultIconKey, overrides, tag)
    return ResolvedEntryAppearance(resolved.label, FpIcons.byKey[resolved.iconKey] ?: icon, resolved.iconKey)
}

@Composable
internal fun EntryCategory.appearance(state: DayEntryUiState) =
    entryAppearance(overrideKey, label, icon, if (category == null) state.overrides else emptyList(), category = category)
@Composable
internal fun EntryItem.appearance(state: DayEntryUiState) = entryAppearance(key, label, icon, state.overrides, tag)

internal fun builtInEntryItems(field: String): List<EntryItem> {
    val values: List<Triple<String, Int, Int>> = when (field) {
        "mood" -> listOf(Mood.BAD, Mood.LOW, Mood.OKAY, Mood.GOOD, Mood.GREAT).map { Triple(it.name, moodLabel(it), moodIcon(it)) }
        "flow" -> FlowLevel.entries.map { Triple(it.name, flowLabel(it), R.drawable.ic_item_drop) }
        "pain" -> Pain.entries.map { Triple(it.name, painLabel(it), R.drawable.ic_symptom_cramps) }
        // Cramps, bloating, headache lead, as in the short list since 1.0.
        "symptoms" -> Symptom.entries.sortedBy { listOf(Symptom.CRAMPS, Symptom.BLOATING, Symptom.HEADACHE).indexOf(it).let { i -> if (i < 0) 3 else i } }.map { Triple(it.name, symptomLabel(it), symptomIcon(it)) }
        "sex" -> Sex.entries.map { Triple(it.name, sexLabel(it), R.drawable.ic_fp_sex) }
        "discharge" -> Discharge.entries.map { Triple(it.name, dischargeLabel(it), R.drawable.ic_fp_discharge) }
        "ovulation_test" -> OvulationTest.entries.map { Triple(it.name,
            if (it == OvulationTest.POSITIVE) R.string.ovulation_positive else R.string.ovulation_negative, R.drawable.ic_fp_today) }
        else -> emptyList()
    }
    return values.mapIndexed { index, (name, label, icon) -> EntryItem("item:$field:$name", name, label, icon, order = index) }
}

internal fun DayEntryUiState.selected(item: EntryItem): Boolean = item.tag?.let { it.id in log.tagIds } ?: when (item.key.split(':')[1]) {
    "mood" -> log.mood?.name == item.name
    "flow" -> log.flow?.name == item.name
    "pain" -> log.pain?.name == item.name
    "sex" -> log.sex?.name == item.name
    "discharge" -> log.discharge?.name == item.name
    "symptoms" -> log.symptoms.any { it.name == item.name }
    "ovulation_test" -> log.ovulationTest?.name == item.name
    else -> false
}

internal fun DayEntryUiState.categoryTags(category: EntryCategory): List<Tag> {
    val id = when {
        category.key in ownItemFields -> customCategories.find { it.builtInField() == category.key }?.id ?: return emptyList()
        category.category != null -> category.category.id
        category.key == "tags" -> null
        else -> return emptyList()
    }
    return tags.filter { it.categoryId == id }
}

internal fun DayEntryUiState.entryItems(category: EntryCategory, includeHidden: Boolean = false): List<EntryItem> {
    val builtIns = builtInEntryItems(category.key)
    val own = categoryTags(category).mapIndexed { index, tag ->
        EntryItem("tag:${tag.id}", null, R.string.item_name, FpIcons.byKey[tag.iconKey] ?: R.drawable.ic_fp_tags,
            tag, order = builtIns.size + index)
    }
    return (builtIns + own).map { item ->
        val override = overrides.find { it.key == item.key }
        item.copy(order = override?.sortOrder ?: item.order, hidden = override?.hidden == true || item.tag?.archived == true)
    }.filter { item -> if (includeHidden) item.tag?.archived != true else !item.hidden || selected(item) }
        .sortedWith(compareBy({ it.order }, { it.key }))
}

internal fun entryCategories(state: DayEntryUiState, includeHidden: Boolean = false): List<EntryCategory> {
    val custom = state.customCategories.filter { it.builtInField() == null && (!it.archived ||
        !includeHidden && state.tags.any { tag -> tag.categoryId == it.id && tag.id in state.log.tagIds }) }.map {
        EntryCategory("custom:${it.id}", R.string.entry_tags, FpIcons.byKey[it.iconKey] ?: R.drawable.ic_fp_tags, it.sortOrder, it)
    }
    return (builtInCategories + custom).map { category ->
        val override = state.overrides.find { it.key == category.overrideKey }
        category.copy(order = override?.sortOrder ?: category.order, hidden = override?.hidden ?: category.defaultHidden)
    }.filter { category ->
        val used = state.entryItems(category).any { state.selected(it) } || category.key == "note" && !state.log.note.isNullOrBlank()
        includeHidden || used || (!category.hidden && (category.key != "ovulation_test" || state.situation.phase == LifePhase.TRYING_TO_CONCEIVE))
    }.sortedWith(compareBy({ it.order }, { it.key }))
}

internal fun DayEntryUiState.itemVisible(field: String, name: String): Boolean =
    overrides.none { it.key == "item:$field:$name" && it.hidden } || selected(builtInEntryItems(field).single { it.name == name })
internal fun DayEntryUiState.visibleTags(category: EntryCategory): List<Tag> = entryItems(category).mapNotNull { it.tag }
/** Only offered in peri-/menopause (or when already logged). */
internal val menopauseSymptoms = setOf(Symptom.HOT_FLUSHES, Symptom.NIGHT_SWEATS, Symptom.BRAIN_FOG, Symptom.JOINT_PAIN,
    Symptom.VAGINAL_DRYNESS, Symptom.HEART_RACING)
/** Shown up front in peri-/menopause; mood swings and low sex drive are also under "More" for everyone. */
private val menopauseShortList = menopauseSymptoms + setOf(Symptom.MOOD_SWINGS, Symptom.LOW_LIBIDO)
internal fun DayEntryUiState.visibleSymptoms(more: Boolean): List<Symptom> {
    val phaseItems = if (situation.phase in setOf(LifePhase.PERIMENOPAUSE, LifePhase.MENOPAUSE)) menopauseShortList else emptySet()
    val base = if (more) Symptom.entries.filter { it !in menopauseSymptoms || it in phaseItems || it in log.symptoms }
        else listOf(Symptom.CRAMPS, Symptom.BLOATING, Symptom.HEADACHE) + phaseItems + log.symptoms
    return base.distinct().filter { itemVisible("symptoms", it.name) }
        .sortedBy { symptom -> overrides.find { it.key == "item:symptoms:${symptom.name}" }?.sortOrder ?: Symptom.entries.indexOf(symptom) }
}
