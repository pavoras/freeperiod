package org.freeperiod.engine.backup

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.*

/** Validates an entire schema-2 snapshot before any restore can mutate storage. */
internal fun validBackup(data: BackupData, today: LocalDate): Boolean {
    if (data.schemaVersion != 2 || data.settings.typicalCycleLength?.let { it !in 15..90 } == true) return false
    if (!uniquePositiveIds(data.periods.map { it.id }) || !uniquePositiveIds(data.tags.map { it.id }) ||
        !uniquePositiveIds(data.customCategories.map { it.id }) || !uniquePositiveIds(data.reminders.map { it.id })) return false
    if (data.dayLogs.map { it.date }.toSet().size != data.dayLogs.size) return false
    val tagIds = data.tags.map { it.id }.toSet()
    val categoryIds = data.customCategories.map { it.id }.toSet()
    val periodIds = data.periods.map { it.id }.toSet()
    if (data.dayLogs.any { !tagIds.containsAll(it.tagIds) } || !periodIds.containsAll(data.hintDismissals)) return false
    if (data.tags.any { it.categoryId != null && it.categoryId !in categoryIds }) return false
    if (data.tags.any { it.name.isBlank() || it.iconKey.isBlank() } ||
        data.customCategories.any { it.name.isBlank() || it.iconKey.isBlank() }) return false
    if (data.tags.map { it.categoryId to it.name.lowercase(Locale.ROOT) }.toSet().size != data.tags.size) return false
    if (data.customCategories.map { it.name.lowercase(Locale.ROOT) }.toSet().size != data.customCategories.size) return false
    if (data.overrides.map { it.key }.toSet().size != data.overrides.size ||
        data.overrides.any { !validOverrideKey(it.key, categoryIds, tagIds) }) return false
    if (data.reminders.any {
        if (it.kind == ReminderKind.PERIOD_DUE) it.daysBefore == null || it.daysBefore < 0 else it.daysBefore != null
    }) return false
    // PillSchedule and Recurrence validate their complete parameters during deserialization.
    var previous: Period? = null
    for (period in data.periods.sortedBy { it.start }) {
        if (PeriodRules.validate(listOfNotNull(previous), period, today) != null) return false
        previous = period
    }
    return true
}

private fun uniquePositiveIds(ids: List<Long>): Boolean = ids.all { it > 0 } && ids.toSet().size == ids.size

private val categories = setOf("flow", "mood", "pain", "symptoms", "sex", "discharge", "tags", "note", "ovulation_test")
private val items: Map<String, Set<String>> = mapOf(
    "flow" to FlowLevel.entries.map { it.name }.toSet(), "mood" to Mood.entries.map { it.name }.toSet(),
    "pain" to Pain.entries.map { it.name }.toSet(), "symptoms" to Symptom.entries.map { it.name }.toSet(),
    "sex" to Sex.entries.map { it.name }.toSet(), "discharge" to Discharge.entries.map { it.name }.toSet(),
    "ovulation_test" to OvulationTest.entries.map { it.name }.toSet(),
)

private fun validOverrideKey(key: String, categoryIds: Set<Long>, tagIds: Set<Long>): Boolean {
    val parts = key.split(':')
    return when (parts.first()) {
        "category" -> parts.size == 2 && parts[1] in categories
        "item" -> parts.size == 3 && parts[2] in items[parts[1]].orEmpty()
        "customCategory" -> parts.size == 2 && parts[1].toLongOrNull() in categoryIds
        "tag" -> parts.size == 2 && parts[1].toLongOrNull() in tagIds
        else -> false
    }
}
