package org.freeperiod.engine.importing

import java.time.LocalDate
import java.util.Locale
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
 * with a FreePeriod counterpart are mapped, your own Clue tags become tags. Every other option is kept
 * under Clue's name: in the matching FreePeriod category when it takes several items a day, else in
 * an own category named after Clue's.
 */
object ClueImport {
    private val flows = mapOf("spotting" to FlowLevel.SPOTTING, "light" to FlowLevel.LIGHT,
        "medium" to FlowLevel.MEDIUM, "heavy" to FlowLevel.HEAVY, "very_heavy" to FlowLevel.HEAVY)
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
    private val sexes = mapOf("unprotected" to Sex.UNPROTECTED, "withdrawal" to Sex.UNPROTECTED,
        "unprotected_sex" to Sex.UNPROTECTED, "withdrawal_sex" to Sex.UNPROTECTED,
        "protected" to Sex.PROTECTED, "protected_sex" to Sex.PROTECTED)
    private val libidos = mapOf("high_sex_drive" to Libido.HIGH, "low_sex_drive" to Libido.LOW)
    /** Clue categories whose other options belong with FreePeriod's own items of a built-in category. */
    private val fields = mapOf("pain" to "symptoms", "mood" to "symptoms", "feelings" to "symptoms",
        "energy" to "symptoms", "poop" to "symptoms", "digestion" to "symptoms", "sex" to "sex", "sex_life" to "sex",
        "fluid" to "discharge", "discharge" to "discharge")
    /** Keys that are not categories, and your own tags (they come in as tags). */
    private val skipped = setOf("day", "date", "id", "tags")

    /** Null when the file is not a Clue backup or holds nothing to import. */
    fun parse(bytes: ByteArray, today: LocalDate): AppImport? = try {
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        val entries = if (root is JsonArray) measurements(root) else root.jsonObject.getValue("data").jsonArray.map { entry ->
            val day = entry.jsonObject
            LocalDate.parse(day.getValue("day").jsonPrimitive.content.take(10)) to day.mapValues { values(it.value) }
        }
        val logs = mutableMapOf<LocalDate, DayLog>()
        val tags = mutableMapOf<LocalDate, Set<String>>()
        val items = mutableMapOf<LocalDate, Set<AppItem>>()
        val flow = mutableMapOf<LocalDate, FlowLevel?>()
        for ((day, entry) in entries) {
            if (day > today || day in logs) continue
            fun values(vararg keys: String) = keys.flatMap { entry[it].orEmpty() }
            val bleeding = values("period").firstNotNullOfOrNull { flows[it] }
                ?: FlowLevel.SPOTTING.takeIf { values("spotting").isNotEmpty() }
            if (bleeding != null) flow[day] = bleeding
            val sex = values("sex", "sex_life")
            val log = DayLog(day,
                flow = bleeding,
                mood = if ("happy" in values("mood", "feelings")) Mood.GOOD else null,
                symptoms = values("pain", "mood", "feelings", "energy", "poop", "digestion").mapNotNull { symptoms[it] }.toSet() +
                    (if (values("craving").isNotEmpty()) setOf(Symptom.CRAVINGS) else emptySet()),
                sex = sex.mapNotNull { sexes[it] }.toSet(),
                libido = sex.firstNotNullOfOrNull { libidos[it] },
                discharge = values("fluid", "discharge").mapNotNull { discharges[it] }.toSet())
            logs[day] = log
            values("tags").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                .takeIf { it.isNotEmpty() }?.let { tags[day] = it }
            entry.flatMap { (category, options) -> if (category in skipped) emptyList() else options.filter {
                !mapped(category, it) && it.isNotBlank() && it.toDoubleOrNull() == null && it != "true" && it != "false"
            }.map { AppItem(name(it), name(category), fields[category]) } }.toSet().takeIf { it.isNotEmpty() }?.let { items[day] = it }
        }
        AppImport(BleedingDays.periods(flow, today), logs.values.filterNot { it.isEmpty() }.sortedBy { it.date },
            tags.toSortedMap(), items.toSortedMap()).takeUnless { it.isEmpty() }
    } catch (_: Exception) { null }

    /** Whether the option already became a FreePeriod value (spotting colours count as spotting). */
    private fun mapped(category: String, option: String): Boolean = when (category) {
        "period" -> option in flows
        "pain", "energy", "poop", "digestion" -> option in symptoms
        "mood", "feelings" -> option in symptoms || option == "happy"
        "sex", "sex_life" -> option in sexes || option in libidos
        "fluid", "discharge" -> option in discharges
        else -> false
    }

    /** Clue's keys as names: "period_underwear" becomes "Period underwear"; you can rename them in FreePeriod. */
    private fun name(key: String) = key.replace('_', ' ').trim().replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** The download's entries ({type, date, value}) gathered per day and category. */
    private fun measurements(root: JsonArray): List<Pair<LocalDate, Map<String, List<String>>>> =
        root.map { it.jsonObject }.groupBy { LocalDate.parse(it.getValue("date").jsonPrimitive.content.take(10)) }
            .map { (day, entries) ->
                day to entries.mapNotNull { entry ->
                    entry["type"]?.jsonPrimitive?.contentOrNull?.let { it to values(entry["value"]) }
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
