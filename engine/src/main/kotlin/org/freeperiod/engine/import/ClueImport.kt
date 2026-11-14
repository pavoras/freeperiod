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
 * Reads Clue's backup (.cluedata, JSON with one entry per day) and the measurements.json from Clue's
 * "Download my data" ZIP (one entry per day and category). Bleeding gives periods and flow; options
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
        "constipated" to Symptom.DIGESTION, "diarrhea" to Symptom.DIGESTION,
        "breast_tenderness" to Symptom.BREAST_TENDERNESS, "lower_back" to Symptom.BACKACHE,
        "migraine" to Symptom.HEADACHE, "migraine_with_aura" to Symptom.HEADACHE, "fully_energized" to Symptom.ENERGETIC,
        "bloated" to Symptom.BLOATING, "nauseated" to Symptom.NAUSEA)
    private val discharges = mapOf("sticky" to Discharge.STICKY, "creamy" to Discharge.CREAMY,
        "egg_white" to Discharge.EGG_WHITE, "atypical" to Discharge.UNUSUAL, "watery" to Discharge.WATERY)
    /** Categories of Clue's data download, under the names of the backup's day entries. */
    private val categories = mapOf("period" to "period", "spotting" to "spotting", "pain" to "pain",
        "feelings" to "mood", "energy" to "energy", "craving" to "craving", "sex_life" to "sex",
        "discharge" to "fluid", "digestion" to "poop", "poop" to "poop", "tags" to "tags")

    /** Null when the file is not a Clue backup or holds nothing to import. */
    fun parse(bytes: ByteArray, today: LocalDate): AppImport? = try {
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        val entries = if (root is JsonArray) measurements(root) else root.jsonObject.getValue("data").jsonArray.map { entry ->
            val day = entry.jsonObject
            LocalDate.parse(day.getValue("day").jsonPrimitive.content.take(10)) to day.mapValues { values(it.value) }
        }
        val logs = mutableMapOf<LocalDate, DayLog>()
        val tags = mutableMapOf<LocalDate, Set<String>>()
        val flow = mutableMapOf<LocalDate, FlowLevel?>()
        for ((day, entry) in entries) {
            fun values(key: String) = entry[key].orEmpty()
            if (day > today || day in logs) continue
            val bleeding = values("period").firstNotNullOfOrNull { flows[it] }
                ?: FlowLevel.SPOTTING.takeIf { values("spotting").isNotEmpty() }
            if (bleeding != null) flow[day] = bleeding
            val sex = values("sex")
            val log = DayLog(day,
                flow = bleeding,
                mood = if ("happy" in values("mood")) Mood.GOOD else null,
                symptoms = listOf("pain", "mood", "energy", "poop").flatMap { values(it) }
                    .mapNotNull { symptoms[it] }.toSet() +
                    (if (values("craving").isNotEmpty()) setOf(Symptom.CRAVINGS) else emptySet()),
                sex = sex.mapNotNull { when (it) {
                    "unprotected", "withdrawal", "unprotected_sex", "withdrawal_sex" -> Sex.UNPROTECTED
                    "protected", "protected_sex" -> Sex.PROTECTED
                    else -> null
                } }.toSet(),
                libido = when {
                    "high_sex_drive" in sex -> Libido.HIGH
                    "low_sex_drive" in sex -> Libido.LOW
                    else -> null
                },
                discharge = values("fluid").mapNotNull { discharges[it] }.toSet())
            logs[day] = log
            values("tags").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                .takeIf { it.isNotEmpty() }?.let { tags[day] = it }
        }
        AppImport(BleedingDays.periods(flow, today), logs.values.filterNot { it.isEmpty() }.sortedBy { it.date },
            tags.toSortedMap()).takeUnless { it.isEmpty() }
    } catch (_: Exception) { null }

    /** The download's entries ({type, date, value}) gathered per day, under the backup's category names. */
    private fun measurements(root: JsonArray): List<Pair<LocalDate, Map<String, List<String>>>> =
        root.map { it.jsonObject }.groupBy { LocalDate.parse(it.getValue("date").jsonPrimitive.content.take(10)) }
            .map { (day, items) ->
                day to items.mapNotNull { item ->
                    categories[item["type"]?.jsonPrimitive?.contentOrNull]?.let { it to values(item["value"]) }
                }.groupBy({ it.first }, { it.second }).mapValues { it.value.flatten() }
            }

    /** Clue stores options as one string, a list of strings, {"option": …} or a list of those. */
    private fun values(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.flatMap { values(it) }
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonObject -> values(element["option"])
        null -> emptyList()
    }
}
