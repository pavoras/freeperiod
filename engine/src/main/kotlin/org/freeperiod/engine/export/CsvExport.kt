package org.freeperiod.engine.export

import java.util.Locale
import org.freeperiod.engine.DayLog
import org.freeperiod.engine.CustomCategory
import org.freeperiod.engine.Period
import org.freeperiod.engine.Tag

/** Unencrypted RFC 4180 exports with ISO dates and stable lowercase enum names. */
object CsvExport {
    /** Exports days in date order, symptoms by name, and referenced tag names by ID. */
    fun days(logs: List<DayLog>, tags: List<Tag>, customCategories: List<CustomCategory> = emptyList()): String {
        val byId = tags.associateBy { it.id }
        val categoryNames = customCategories.associate { it.id to it.name }
        return buildString {
            appendRow(listOf("date", "flow", "mood", "pain", "sex", "discharge", "symptoms", "tags", "note", "ovulation_test", "custom_items"))
            logs.sortedBy { it.date }.forEach { log ->
                appendRow(listOf(
                    log.date.toString(), name(log.flow), name(log.mood), name(log.pain), name(log.sex), name(log.discharge),
                    log.symptoms.sortedBy { it.name }.joinToString(";") { name(it) },
                    log.tagIds.sorted().mapNotNull { byId[it]?.takeIf { it.categoryId == null }?.name }.joinToString(";"), log.note.orEmpty(),
                    name(log.ovulationTest), log.tagIds.sorted().mapNotNull { id ->
                        val tag = byId[id] ?: return@mapNotNull null
                        val category = categoryNames[tag.categoryId] ?: return@mapNotNull null
                        "$category:${tag.name}"
                    }.joinToString(";"),
                ))
            }
        }
    }

    /** Exports inclusive period boundaries in start order with auto/include/exclude cycle use. */
    fun periods(periods: List<Period>): String = buildString {
        appendRow(listOf("start", "end", "cycle_use"))
        periods.sortedBy { it.start }.forEach { appendRow(listOf(it.start.toString(), it.end?.toString().orEmpty(), name(it.cycleUse))) }
    }

    private fun name(value: Enum<*>?): String = value?.name?.lowercase(Locale.ROOT).orEmpty()

    private fun StringBuilder.appendRow(fields: List<String>) {
        append(fields.joinToString(",") { field ->
            if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"${field.replace("\"", "\"\"")}\"" else field
        })
        append("\r\n")
    }
}
