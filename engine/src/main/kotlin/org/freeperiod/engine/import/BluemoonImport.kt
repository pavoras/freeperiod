package org.freeperiod.engine.importing

import java.time.LocalDate
import org.freeperiod.engine.*

/** One row of Bluemoon's Sex table with the contraceptives logged for it. */
data class BluemoonSex(val date: String, val contraceptives: Set<String>)

/**
 * Maps the rows of Bluemoon's backup (an SQLite file the app reads): bleeding days with their strength give
 * periods and flow, sex days with any contraceptive count as protected. Pill rows are not taken.
 */
object BluemoonImport {
    /** [bleeding] holds date (yyyy-MM-dd) to strength (SPOTTING, LIGHT, MEDIUM, HEAVY or null). */
    fun map(bleeding: List<Pair<String, String?>>, sex: List<BluemoonSex>, today: LocalDate): AppImport? = try {
        val flow = bleeding.associate { (date, strength) ->
            LocalDate.parse(date.take(10)) to strength?.let { level ->
                FlowLevel.entries.find { it.name == level && it != FlowLevel.NONE } }
        }.filterKeys { it <= today }
        val protection = sex.groupBy { LocalDate.parse(it.date.take(10)) }.filterKeys { it <= today }
            .mapValues { (_, rows) -> if (rows.all { it.contraceptives.isNotEmpty() }) Sex.PROTECTED else Sex.UNPROTECTED }
        val logs = (flow.keys + protection.keys).sorted()
            .map { DayLog(it, flow = flow[it], sex = setOfNotNull(protection[it])) }.filterNot { it.isEmpty() }
        AppImport(BleedingDays.periods(flow, today), logs).takeUnless { it.isEmpty() }
    } catch (_: Exception) { null }
}
