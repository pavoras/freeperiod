package org.freeperiod.engine.importing

import java.time.LocalDate
import java.util.Locale
import org.freeperiod.engine.*

/** One row of Bluemoon's Sex table (type SOLO, PARTNER or GROUP) with the contraceptives logged for it. */
data class BluemoonSex(val date: String, val contraceptives: Set<String>, val type: String? = null)

/**
 * Maps the rows of Bluemoon's backup (an SQLite file the app reads): bleeding days with their strength give
 * periods and flow; sex with a partner counts as protected when a contraceptive was logged for it. The kind of sex,
 * the contraceptives and the pills taken come in as items under Bluemoon's names.
 */
object BluemoonImport {
    /** [bleeding] holds date (yyyy-MM-dd) to strength (SPOTTING, LIGHT, MEDIUM, HEAVY or null), [pills] the days a pill was taken. */
    fun map(bleeding: List<Pair<String, String?>>, sex: List<BluemoonSex>, today: LocalDate,
            pills: List<String> = emptyList()): AppImport? = try {
        val flow = bleeding.associate { (date, strength) ->
            LocalDate.parse(date.take(10)) to strength?.let { level ->
                FlowLevel.entries.find { it.name == level && it != FlowLevel.NONE } }
        }.filterKeys { it <= today }
        val sexDays = sex.groupBy { LocalDate.parse(it.date.take(10)) }.filterKeys { it <= today }
        // Solo sex says nothing about protection.
        val protection = sexDays.mapValues { (_, rows) -> rows.filter { it.type != "SOLO" } }.filterValues { it.isNotEmpty() }
            .mapValues { (_, rows) -> if (rows.all { it.contraceptives.any(::used) }) Sex.PROTECTED else Sex.UNPROTECTED }
        val items = sexDays.mapValues { (_, rows) ->
            rows.flatMap { row -> row.contraceptives.filter(::used).map { AppItem(name(it), "Contraceptive", "sex") } +
                listOfNotNull(row.type?.takeIf { it.isNotBlank() }?.let { AppItem(name(it), "Sex type", "sex") }) }.toSet()
        }.toMutableMap()
        pills.map { LocalDate.parse(it.take(10)) }.filter { it <= today }.forEach { date ->
            items[date] = items[date].orEmpty() + AppItem("Pill", "Medication", "medication")
        }
        val logs = (flow.keys + protection.keys).sorted()
            .map { DayLog(it, flow = flow[it], sex = setOfNotNull(protection[it])) }.filterNot { it.isEmpty() }
        AppImport(BleedingDays.periods(flow, today), logs, items = items.filterValues { it.isNotEmpty() })
            .takeUnless { it.isEmpty() }
    } catch (_: Exception) { null }

    private fun used(contraceptive: String) = contraceptive.isNotBlank() && contraceptive != "NONE"

    /** CONDOM becomes Condom; short names like IUD stay as they are. */
    private fun name(key: String) = key.trim().replace('_', ' ')
        .let { if (it.length <= 3) it else it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }
}
