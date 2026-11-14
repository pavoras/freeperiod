package org.freeperiod.engine.importing

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.*
import org.freeperiod.engine.Period

/**
 * Periods and diary days read from another app's file. [tagNames] are that app's own tags per day; they
 * become uncategorized FreePeriod tags (an existing one with the same name is reused) when imported.
 * [items] are the app's options without a FreePeriod counterpart; they become tags in a category.
 */
data class AppImport(val periods: List<Period>, val dayLogs: List<DayLog>,
    val tagNames: Map<LocalDate, Set<String>> = emptyMap(), val items: Map<LocalDate, Set<AppItem>> = emptyMap()) {
    /** Days with a diary entry, a tag or an item. */
    val days: Int get() = (dayLogs.map { it.date } + tagNames.keys + items.keys).distinct().size
    fun isEmpty() = periods.isEmpty() && days == 0
}

/**
 * An option from another app with no FreePeriod counterpart. It becomes a tag in the own items of the built-in
 * [field] when that category takes several items a day, else in the own category [category] (found by name or created).
 */
data class AppItem(val name: String, val category: String, val field: String? = null)

/** Shared rules for apps that log bleeding per day instead of period start and end dates. */
object BleedingDays {
    /**
     * Logged bleeding days in a row become one period; a single unlogged day in between does not split it.
     * The period runs from the first to the last day heavier than spotting, so runs of spotting only stay
     * diary entries (as in the CSV import). A null level is bleeding without a strength and counts.
     * A period still bleeding on [today] comes in as ongoing.
     */
    fun periods(days: Map<LocalDate, FlowLevel?>, today: LocalDate): List<Period> {
        val bleeding = days.filterValues { it != FlowLevel.NONE }.toSortedMap()
        val runs = mutableListOf<MutableList<LocalDate>>()
        for (day in bleeding.keys) {
            val run = runs.lastOrNull()
            if (run != null && day <= run.last().plusDays(2)) run += day else runs += mutableListOf(day)
        }
        return runs.mapNotNull { run ->
            val core = run.filter { bleeding[it] != FlowLevel.SPOTTING }
            if (core.isEmpty()) null else Period(0, core.first(), core.last().takeIf { it < today })
        }
    }

    /**
     * Adds an imported day to what is already logged there: what you logged in FreePeriod stays, missing
     * values are filled in, symptoms and tags are combined. Returns [existing] when the result would not
     * be a valid entry.
     */
    fun merge(existing: DayLog?, imported: DayLog, tags: List<Tag>, categories: List<CustomCategory>): DayLog {
        if (existing == null) return imported
        fun hasOwn(field: String): Boolean {
            val marker = categories.find { it.builtInField() == field } ?: return false
            return tags.any { it.categoryId == marker.id && it.id in existing.tagIds }
        }
        val merged = existing.copy(
            flow = existing.flow ?: imported.flow,
            mood = existing.mood ?: imported.mood,
            symptoms = existing.symptoms + imported.symptoms,
            pain = existing.pain ?: imported.pain,
            sex = if (categories.isSingleChoice("sex") && hasOwn("sex")) existing.sex else existing.sex.ifEmpty { imported.sex },
            discharge = if (categories.isSingleChoice("discharge") && hasOwn("discharge")) existing.discharge
                else existing.discharge.ifEmpty { imported.discharge },
            note = existing.note ?: imported.note,
            tagIds = existing.tagIds + imported.tagIds,
            ovulationTest = existing.ovulationTest ?: imported.ovulationTest,
            appetite = existing.appetite ?: imported.appetite,
            libido = existing.libido ?: imported.libido)
        return if (EntrySelection.valid(merged, tags, categories)) merged else existing
    }

    /** Finds an uncategorized tag by name, ignoring case. */
    fun tagNamed(tags: List<Tag>, name: String): Tag? =
        tags.find { it.categoryId == null && it.name.lowercase(Locale.ROOT) == name.trim().lowercase(Locale.ROOT) }
}
