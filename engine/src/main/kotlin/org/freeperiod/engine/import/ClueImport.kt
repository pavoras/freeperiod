package org.freeperiod.engine.importing

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.freeperiod.engine.*

/**
 * Reads Clue's backup (.cluedata): JSON with one entry per day. Bleeding gives periods and flow; options
 * with a FreePeriod counterpart are mapped, your own Clue tags become tags. Clue's other categories
 * (sleep, social, motivation and the like) have no counterpart and are left out.
 */
object ClueImport {
    private val flows = mapOf("spotting" to FlowLevel.SPOTTING, "light" to FlowLevel.LIGHT,
        "medium" to FlowLevel.MEDIUM, "heavy" to FlowLevel.HEAVY)
    private val symptoms = mapOf("cramps" to Symptom.CRAMPS, "headache" to Symptom.HEADACHE,
        "tender_breasts" to Symptom.BREAST_TENDERNESS, "sad" to Symptom.SAD,
        "exhausted" to Symptom.FATIGUE, "low_energy" to Symptom.FATIGUE,
        "energized" to Symptom.ENERGETIC, "high_energy" to Symptom.ENERGETIC,
        "constipated" to Symptom.DIGESTION, "diarrhea" to Symptom.DIGESTION)
    private val discharges = mapOf("sticky" to Discharge.STICKY, "creamy" to Discharge.CREAMY,
        "egg_white" to Discharge.EGG_WHITE, "atypical" to Discharge.UNUSUAL)

    /** Null when the file is not a Clue backup or holds nothing to import. */
    fun parse(bytes: ByteArray, today: LocalDate): AppImport? = try {
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        val entries = root.getValue("data").jsonArray.map { it.jsonObject }
        val logs = mutableMapOf<LocalDate, DayLog>()
        val tags = mutableMapOf<LocalDate, Set<String>>()
        val flow = mutableMapOf<LocalDate, FlowLevel?>()
        for (entry in entries) {
            val day = LocalDate.parse(entry.getValue("day").jsonPrimitive.content.take(10))
            if (day > today || day in logs) continue
            val bleeding = values(entry["period"]).firstNotNullOfOrNull { flows[it] }
            if (bleeding != null) flow[day] = bleeding
            val sex = values(entry["sex"])
            val log = DayLog(day,
                flow = bleeding,
                mood = if ("happy" in values(entry["mood"])) Mood.GOOD else null,
                symptoms = listOf("pain", "mood", "energy", "poop").flatMap { values(entry[it]) }
                    .mapNotNull { symptoms[it] }.toSet() +
                    (if (values(entry["craving"]).isNotEmpty()) setOf(Symptom.CRAVINGS) else emptySet()),
                sex = when {
                    "unprotected" in sex || "withdrawal" in sex -> Sex.UNPROTECTED
                    "protected" in sex -> Sex.PROTECTED
                    else -> null
                },
                libido = when {
                    "high_sex_drive" in sex -> Libido.HIGH
                    "low_sex_drive" in sex -> Libido.LOW
                    else -> null
                },
                discharge = values(entry["fluid"]).firstNotNullOfOrNull { discharges[it] })
            logs[day] = log
            values(entry["tags"]).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                .takeIf { it.isNotEmpty() }?.let { tags[day] = it }
        }
        AppImport(BleedingDays.periods(flow, today), logs.values.filterNot { it.isEmpty() }.sortedBy { it.date },
            tags.toSortedMap()).takeUnless { it.isEmpty() }
    } catch (_: Exception) { null }

    /** Clue stores some options as one string and others as a list of strings. */
    private fun values(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonObject, null -> emptyList()
    }
}
