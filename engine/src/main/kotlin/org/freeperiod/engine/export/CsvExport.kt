package org.freeperiod.engine.export

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.DayLog
import org.freeperiod.engine.CustomCategory
import org.freeperiod.engine.Period
import org.freeperiod.engine.Tag

/**
 * Unencrypted RFC 4180 export in one file: one row per period day or logged day, ISO dates and stable
 * lowercase enum names. FreePeriod.'s own CSV import reads the period days back from `period_day`.
 */
object CsvExport {
    private val header = listOf("date", "period_day", "flow", "mood", "pain", "sex", "discharge", "symptoms", "tags", "note",
        "ovulation_test", "custom_items")

    /** An ongoing period counts up to [today]; items of custom categories are written as category:item. */
    fun export(periods: List<Period>, logs: List<DayLog>, tags: List<Tag>, customCategories: List<CustomCategory> = emptyList(),
        today: LocalDate): String {
        val periodDays = periods.flatMap { period ->
            generateSequence(period.start) { it.plusDays(1) }.takeWhile { it <= (period.end ?: today) }.toList()
        }.toSet()
        val byDate = logs.associateBy { it.date }
        val byId = tags.associateBy { it.id }
        val categoryNames = customCategories.associate { it.id to it.name }
        return buildString {
            appendRow(header)
            (periodDays + byDate.keys).sorted().forEach { date ->
                val log = byDate[date] ?: DayLog(date)
                appendRow(listOf(
                    date.toString(), if (date in periodDays) "yes" else "", name(log.flow), name(log.mood), name(log.pain),
                    name(log.sex), name(log.discharge), log.symptoms.sortedBy { it.name }.joinToString(";") { name(it) },
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

    private fun name(value: Enum<*>?): String = value?.name?.lowercase(Locale.ROOT).orEmpty()

    private fun StringBuilder.appendRow(fields: List<String>) {
        append(fields.joinToString(",") { field ->
            if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"${field.replace("\"", "\"\"")}\"" else field
        })
        append("\r\n")
    }
}
