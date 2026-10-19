package org.freeperiod.engine.importing

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.freeperiod.engine.Period

sealed interface FloImportResult {
    data class Parsed(val periods: List<Period>) : FloImportResult
    data object NeedsPassword : FloImportResult
    data object WrongPassword : FloImportResult
    data object Invalid : FloImportResult
}

/**
 * Reads the data export Flo's support sends: a password-protected ZIP with one JSON file, or that JSON
 * unpacked. Only period start and end dates are taken; Flo's predictions and pregnancy flags are not.
 */
object FloImport {
    fun parse(bytes: ByteArray, password: CharArray? = null, today: LocalDate): FloImportResult = try {
        val json = if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val zip = ZipEntries(bytes)
            val entry = zip.entries.filter { it.name.endsWith(".json", ignoreCase = true) }.maxByOrNull { it.size }
                ?: return FloImportResult.Invalid
            zip.read(entry, password)
        } else bytes
        val periods = try { periods(json.toString(Charsets.UTF_8), today) } finally { if (json !== bytes) json.fill(0) }
        if (periods.isEmpty()) FloImportResult.Invalid else FloImportResult.Parsed(periods)
    } catch (_: ZipPasswordRequired) {
        FloImportResult.NeedsPassword
    } catch (_: ZipWrongPassword) {
        FloImportResult.WrongPassword
    } catch (_: Exception) {
        FloImportResult.Invalid
    }

    private fun periods(text: String, today: LocalDate): List<Period> {
        val cycles = Json.parseToJsonElement(text.removePrefix("﻿")).jsonObject["operationalData"]
            ?.jsonObject?.get("cycles")?.jsonArray ?: return emptyList()
        return cycles.mapNotNull { cycle ->
            val fields = cycle as? JsonObject ?: return@mapNotNull null
            val start = date(fields, "period_start_date") ?: return@mapNotNull null
            // A period that has not ended (or ends in the future) comes in as ongoing.
            val end = date(fields, "period_end_date")?.takeIf { it >= start && it <= today }
            Period(0, start, end).takeIf { start <= today }
        }.distinctBy { it.start }.sortedBy { it.start }
    }

    /** Flo writes local dates as "2025-01-22 00:00:00.0". */
    private fun date(fields: JsonObject, key: String): LocalDate? =
        fields[key]?.jsonPrimitive?.contentOrNull?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
