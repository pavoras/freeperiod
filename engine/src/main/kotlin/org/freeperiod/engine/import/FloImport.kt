package org.freeperiod.engine.importing

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.freeperiod.engine.DayLog
import org.freeperiod.engine.FlowLevel
import org.freeperiod.engine.Period

sealed interface FloImportResult {
    data class Parsed(val periods: List<Period>, val dayLogs: List<DayLog> = emptyList()) : FloImportResult
    data object NeedsPassword : FloImportResult
    data object WrongPassword : FloImportResult
    data object Invalid : FloImportResult
}

/**
 * Reads the data export Flo's support sends: a password-protected ZIP with one JSON file, or that JSON
 * unpacked. Period start and end dates and the strength of the bleeding per day are taken; Flo's predictions and
 * pregnancy flags are not.
 */
object FloImport {
    fun parse(bytes: ByteArray, password: CharArray? = null, today: LocalDate): FloImportResult = try {
        val json = if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val zip = ZipEntries(bytes)
            val entry = zip.entries.filter { it.name.endsWith(".json", ignoreCase = true) }.maxByOrNull { it.size }
                ?: return FloImportResult.Invalid
            zip.read(entry, password)
        } else bytes
        val cycles = try { cycles(json.toString(Charsets.UTF_8)) } finally { if (json !== bytes) json.fill(0) }
        val periods = periods(cycles, today)
        if (periods.isEmpty()) FloImportResult.Invalid else FloImportResult.Parsed(periods, flow(cycles, today))
    } catch (_: ZipPasswordRequired) {
        FloImportResult.NeedsPassword
    } catch (_: ZipWrongPassword) {
        FloImportResult.WrongPassword
    } catch (_: Exception) {
        FloImportResult.Invalid
    }

    private fun cycles(text: String): List<JsonObject> =
        Json.parseToJsonElement(text.removePrefix("﻿")).jsonObject["operationalData"]
            ?.jsonObject?.get("cycles")?.jsonArray?.filterIsInstance<JsonObject>().orEmpty()

    private fun periods(cycles: List<JsonObject>, today: LocalDate): List<Period> =
        cycles.mapNotNull { fields ->
            val start = date(fields, "period_start_date") ?: return@mapNotNull null
            // A period that has not ended (or ends in the future) comes in as ongoing.
            val end = date(fields, "period_end_date")?.takeIf { it >= start && it <= today }
            Period(0, start, end).takeIf { start <= today }
        }.distinctBy { it.start }.sortedBy { it.start }

    /**
     * Flo keeps the strength per period day as JSON text like {"0": 3, "1": 2}: days counted from the period's
     * start, 1 light, 2 medium, 3 heavy. Days without a strength stay without one.
     */
    private fun flow(cycles: List<JsonObject>, today: LocalDate): List<DayLog> = cycles.flatMap { fields ->
        val start = date(fields, "period_start_date") ?: return@flatMap emptyList()
        val value = fields["period_intensity"]
        val days = runCatching {
            (if (value is JsonPrimitive && value.isString) Json.parseToJsonElement(value.content) else value)?.jsonObject
        }.getOrNull() ?: return@flatMap emptyList()
        days.mapNotNull { (day, level) ->
            val flow = when ((level as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()) {
                1 -> FlowLevel.LIGHT; 2 -> FlowLevel.MEDIUM; 3 -> FlowLevel.HEAVY; else -> null }
            val date = day.toLongOrNull()?.takeIf { it in 0..60 }?.let { start.plusDays(it) }
            if (flow == null || date == null || date > today) null else DayLog(date, flow = flow)
        }
    }.distinctBy { it.date }.sortedBy { it.date }

    /** Flo writes local dates as "2025-01-22 00:00:00.0". */
    private fun date(fields: JsonObject, key: String): LocalDate? =
        fields[key]?.jsonPrimitive?.contentOrNull?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
