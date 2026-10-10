package org.freeperiod.engine.importing

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import org.freeperiod.engine.*
import org.freeperiod.engine.Period

enum class CsvDateFormat { ISO, DAY_MONTH_YEAR, MONTH_DAY_YEAR }

sealed interface CsvImportResult {
    data class Parsed(val periods: List<Period>, val dayLogs: List<DayLog> = emptyList(),
        val tags: List<Tag> = emptyList(), val customCategories: List<CustomCategory> = emptyList()) : CsvImportResult
    data class NeedsFormat(val formats: List<CsvDateFormat>) : CsvImportResult
    data object Invalid : CsvImportResult
}

/** Imports period boundaries, bleeding-day rows and diary entries without dropping malformed data. */
object CsvImport {
    fun parse(text: String, format: CsvDateFormat? = null, tags: List<Tag> = emptyList(),
        customCategories: List<CustomCategory> = emptyList()): CsvImportResult {
        val records = records(text.removePrefix("\uFEFF")) ?: return CsvImportResult.Invalid
        if (records.isEmpty()) return CsvImportResult.Invalid
        val hasHeader = records.first().none { value -> CsvDateFormat.entries.any { date(value, it) != null } }
        val header = if (hasHeader) records.first().map { it.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit) }
            else records.first().map { "" }
        val rows = if (hasHeader) records.drop(1) else records
        if (rows.isEmpty()) return CsvImportResult.Invalid
        if (rows.any { it.size != header.size }) return CsvImportResult.Invalid
        val start = header.indexOfFirst { it in setOf("start", "startdate", "periodstart", "periodstartdate", "beginn", "startdatum") }
        val end = header.indexOfFirst { it in setOf("end", "enddate", "periodend", "periodenddate", "ende", "enddatum") }
        if (end >= 0 && start < 0) return CsvImportResult.Invalid
        val namedDate = header.indexOfFirst { it in setOf("date", "day", "datum", "bleedingdate") }
        val columns = if (start >= 0) listOf(start) else if (namedDate >= 0) listOf(namedDate) else header.indices.toList()
        val results = columns.flatMap { column ->
            (format?.let(::listOf) ?: CsvDateFormat.entries).mapNotNull { candidate ->
                val periods = periods(rows, header, column, if (start >= 0) end else -1, candidate) ?: return@mapNotNull null
                Triple(column, candidate, periods)
            }
        }
        if (results.isEmpty() || results.map { it.first }.distinct().size != 1) return CsvImportResult.Invalid
        val diary = start < 0 && diaryHeader(header)
        val datesDiffer = diary && results.map { result -> rows.map { date(it[result.first], result.second) } }.distinct().size > 1
        if (results.map { it.third }.distinct().size > 1 || datesDiffer)
            return CsvImportResult.NeedsFormat(results.map { it.second })
        val chosen = results.first()
        return if (diary) diary(rows, header, chosen.first, chosen.second,
            chosen.third, tags, customCategories) ?: CsvImportResult.Invalid
        else CsvImportResult.Parsed(chosen.third)
    }

    private fun diaryHeader(header: List<String>): Boolean =
        header.any { it in ownItemFields || it in setOf("flow", "ovulationtest", "tags", "customitems") }

    private fun periods(rows: List<List<String>>, header: List<String>, start: Int, end: Int, format: CsvDateFormat): List<Period>? {
        val cycle = header.indexOf("cycleuse")
        val flow = header.indexOf("flow")
        // A yes/no bleeding column (also FreePeriod.'s own period_day) decides on its own; flow only without it.
        val bleeding = header.indexOfFirst { it in setOf("bleeding", "periodday") }
        val result = mutableListOf<Period>()
        for (row in rows) {
            val from = date(row[start], format) ?: return null
            if (end >= 0) {
                val to = if (row[end].isBlank()) null else date(row[end], format) ?: return null
                if (to != null && to < from) return null
                val use = if (cycle < 0) CycleUse.AUTO else runCatching { CycleUse.valueOf(row[cycle].trim().uppercase(Locale.ROOT)) }.getOrNull() ?: return null
                result += Period(0, from, to, use)
            } else {
                if (flow < 0 && bleeding < 0 && diaryHeader(header)) continue
                if (flow >= 0 && bleeding < 0) {
                    when (row[flow].trim().lowercase(Locale.ROOT)) {
                        "light", "medium", "heavy" -> Unit
                        "", "none", "spotting" -> continue
                        else -> return null
                    }
                }
                if (bleeding >= 0) when (row[bleeding].trim().lowercase(Locale.ROOT)) {
                    "1", "true", "yes", "ja" -> Unit
                    "0", "false", "no", "nein", "" -> continue
                    else -> return null
                }
                result += Period(0, from, from)
            }
        }
        if (result.isEmpty()) return if (diaryHeader(header)) emptyList() else null
        if (end >= 0) return result.distinct().sortedBy { it.start }
        val days = result.map { it.start }.distinct().sorted()
        val blocks = mutableListOf<Period>()
        var from = days.first()
        var to = from
        for (day in days.drop(1)) {
            if (day != to.plusDays(1)) { blocks += Period(0, from, to); from = day }
            to = day
        }
        blocks += Period(0, from, to)
        return blocks
    }

    private fun diary(rows: List<List<String>>, header: List<String>, dateColumn: Int, format: CsvDateFormat,
        periods: List<Period>, existingTags: List<Tag>, existingCategories: List<CustomCategory>): CsvImportResult.Parsed? {
        val tags = existingTags.toMutableList()
        val categories = existingCategories.toMutableList()
        var nextTag = (tags.maxOfOrNull { it.id } ?: 0) + 1
        var nextCategory = (categories.maxOfOrNull { it.id } ?: 0) + 1
        fun category(name: String, field: String? = null): Long {
            val existing = if (field != null) categories.find { it.builtInField() == field }
                else categories.find { it.builtInField() == null && it.name.equals(name, ignoreCase = true) }
            if (existing != null) return existing.id
            var uniqueName = name
            var suffix = 2
            while (categories.any { it.name.equals(uniqueName, ignoreCase = true) }) uniqueName = "$name ${suffix++}"
            val created = CustomCategory(nextCategory++, uniqueName, field?.let { "builtin:$it" } ?: "tag", 100 + categories.size, false)
            categories += created
            return created.id
        }
        /** "name" or "name:count" (names escape their own colons). */
        fun counted(value: String): Pair<String, Int> {
            val parts = value.split(':')
            require(parts.size in 1..2)
            val times = if (parts.size == 2) requireNotNull(parts[1].toIntOrNull()) else 1
            require(times in 1..MAX_DAILY_COUNT)
            return parts[0] to times
        }
        fun tag(rawName: String, categoryId: Long?): Long {
            val name = csvItemNameDecoded(rawName)
            require(name.isNotBlank())
            val existing = tags.find { it.categoryId == categoryId && it.name.equals(name, ignoreCase = true) }
            if (existing != null) return existing.id
            val created = Tag(nextTag++, name, categoryId = categoryId)
            tags += created
            return created.id
        }
        return try {
            val logs = rows.map { row ->
                fun cell(field: String) = header.indexOf(field).let { if (it < 0) "" else row[it] }
                    .let { if (field == "note") it else it.trim() }
                var log = DayLog(requireNotNull(date(row[dateColumn], format)))
                for (field in singleChoiceFields) {
                    val value = cell(field)
                    if (value.isBlank()) continue
                    if (builtInNames(field).any { it.equals(value, ignoreCase = true) })
                        log = EntrySelection.builtIn(log, field, value.uppercase(Locale.ROOT), tags, categories)
                    else log = EntrySelection.tag(log, tag(value, category("builtin:$field", field)), tags, categories)
                }
                val symptoms = cell("symptoms").split(';').filter { it.isNotBlank() }
                symptoms.forEach { value ->
                    if (builtInNames("symptoms").any { it.equals(value, ignoreCase = true) })
                        log = log.copy(symptoms = log.symptoms + Symptom.valueOf(value.uppercase(Locale.ROOT)))
                    else log = log.copy(tagIds = log.tagIds + tag(value, category("builtin:symptoms", "symptoms")))
                }
                cell("tags").split(';').filter { it.isNotBlank() }.forEach { value ->
                    log = log.copy(tagIds = log.tagIds + tag(value, null))
                }
                cell("customitems").split(';').filter { it.isNotBlank() }.forEach { value ->
                    val parts = value.split(':', limit = 2)
                    require(parts.size == 2 && parts[0].isNotBlank())
                    val (name, times) = counted(parts[1])
                    val id = tag(name, category(csvItemNameDecoded(parts[0])))
                    log = EntrySelection.setCount(log, id, times)
                }
                cell("medication").split(';').filter { it.isNotBlank() }.forEach { value ->
                    val (name, times) = counted(value)
                    log = EntrySelection.setCount(log, tag(name, category("builtin:medication", "medication")), times)
                }
                log.copy(flow = cell("flow").takeIf { it.isNotBlank() }?.let { FlowLevel.valueOf(it.uppercase(Locale.ROOT)) },
                    ovulationTest = cell("ovulationtest").takeIf { it.isNotBlank() }?.let { OvulationTest.valueOf(it.uppercase(Locale.ROOT)) },
                    note = cell("note").takeIf { it.isNotEmpty() })
            }
            require(logs.map { it.date }.distinct().size == logs.size)
            CsvImportResult.Parsed(periods, logs.filterNot { it.isEmpty() }, tags, categories)
        } catch (_: IllegalArgumentException) { null }
    }

    private fun date(value: String, format: CsvDateFormat): LocalDate? = runCatching {
        val pattern = when (format) {
            CsvDateFormat.ISO -> "uuuu-MM-dd"
            CsvDateFormat.DAY_MONTH_YEAR -> if ('.' in value) "dd.MM.uuuu" else "dd/MM/uuuu"
            CsvDateFormat.MONTH_DAY_YEAR -> "MM/dd/uuuu"
        }
        LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT))
    }.getOrNull()

    /** RFC 4180 quotes, escaped quotes and multiline fields; also accepts semicolon/tab files. */
    private fun records(text: String): List<List<String>>? {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val counts = linkedMapOf(',' to 0, ';' to 0, '\t' to 0)
        var inQuotes = false
        first.forEach { ch ->
            if (ch == '"') inQuotes = !inQuotes
            else if (!inQuotes && ch in counts) counts[ch] = counts.getValue(ch) + 1
        }
        val delimiter = counts.maxBy { it.value }.key
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var index = 0
        fun cell() { row += field.toString(); field.setLength(0); closed = false }
        fun line() { cell(); if (row.any { it.isNotBlank() }) result += row; row = mutableListOf() }
        while (index < text.length) {
            val ch = text[index++]
            if (quoted) {
                if (ch == '"') {
                    if (index < text.length && text[index] == '"') { field.append('"'); index++ }
                    else { quoted = false; closed = true }
                } else field.append(ch)
            } else when (ch) {
                '"' -> { if (field.isNotBlank() || closed) return null; field.setLength(0); quoted = true }
                delimiter -> cell()
                '\r', '\n' -> { if (ch == '\r' && index < text.length && text[index] == '\n') index++; line() }
                else -> { if (closed && !ch.isWhitespace()) return null; field.append(ch) }
            }
        }
        if (quoted) return null
        if (field.isNotEmpty() || row.isNotEmpty() || closed) line()
        return result
    }
}
