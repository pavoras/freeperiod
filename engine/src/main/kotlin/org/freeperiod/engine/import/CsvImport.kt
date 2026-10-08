package org.freeperiod.engine.importing

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import org.freeperiod.engine.CycleUse
import org.freeperiod.engine.Period

enum class CsvDateFormat { ISO, DAY_MONTH_YEAR, MONTH_DAY_YEAR }

sealed interface CsvImportResult {
    data class Parsed(val periods: List<Period>) : CsvImportResult
    data class NeedsFormat(val formats: List<CsvDateFormat>) : CsvImportResult
    data object Invalid : CsvImportResult
}

/** Imports boundaries or bleeding-day rows. Rejects malformed rows rather than dropping data. */
object CsvImport {
    fun parse(text: String, format: CsvDateFormat? = null): CsvImportResult {
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
        if (results.map { it.third }.distinct().size > 1) return CsvImportResult.NeedsFormat(results.map { it.second })
        return CsvImportResult.Parsed(results.first().third)
    }

    private fun periods(rows: List<List<String>>, header: List<String>, start: Int, end: Int, format: CsvDateFormat): List<Period>? {
        val cycle = header.indexOf("cycleuse")
        val flow = header.indexOf("flow")
        val bleeding = header.indexOf("bleeding")
        val result = mutableListOf<Period>()
        for (row in rows) {
            val from = date(row[start], format) ?: return null
            if (end >= 0) {
                val to = if (row[end].isBlank()) null else date(row[end], format) ?: return null
                if (to != null && to < from) return null
                val use = if (cycle < 0) CycleUse.AUTO else runCatching { CycleUse.valueOf(row[cycle].uppercase(Locale.ROOT)) }.getOrNull() ?: return null
                result += Period(0, from, to, use)
            } else {
                if (flow >= 0) {
                    when (row[flow].lowercase(Locale.ROOT)) {
                        "light", "medium", "heavy" -> Unit
                        "", "none", "spotting" -> continue
                        else -> return null
                    }
                }
                if (bleeding >= 0) when (row[bleeding].lowercase(Locale.ROOT)) {
                    "1", "true", "yes", "ja" -> Unit
                    "0", "false", "no", "nein", "" -> continue
                    else -> return null
                }
                result += Period(0, from, from)
            }
        }
        if (result.isEmpty()) return null
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

    private fun date(value: String, format: CsvDateFormat): LocalDate? = runCatching {
        val pattern = when (format) {
            CsvDateFormat.ISO -> "uuuu-MM-dd"
            CsvDateFormat.DAY_MONTH_YEAR -> if ('.' in value) "dd.MM.uuuu" else "dd/MM/uuuu"
            CsvDateFormat.MONTH_DAY_YEAR -> "MM/dd/uuuu"
        }
        LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT))
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
        fun cell() { row += field.toString().trim(); field.setLength(0); closed = false }
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
