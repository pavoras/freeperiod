package org.freeperiod.engine

/** Marker categories keep own items in tags and hold built-in choice settings. */
val ownItemFields = setOf("mood", "pain", "symptoms", "sex", "discharge", "medication")
val singleChoiceFields = setOf("mood", "pain")
val configurableChoiceFields = setOf("sex", "discharge")

/** Marker settings also apply to built-in items; categories without a marker use their default. */
fun List<CustomCategory>.isSingleChoice(field: String): Boolean = field in singleChoiceFields ||
    (find { it.builtInField() == field }?.singleChoice ?: (field == "sex"))
/** Fields whose items are counted per day (tap once, then +/-). */
val countedFields = setOf("medication")
const val MAX_DAILY_COUNT = 99

fun CustomCategory.isCounted(): Boolean = counted || builtInField() in countedFields

/** Removes items together with their counts. */
fun DayLog.withoutTags(ids: Set<Long>): DayLog = copy(tagIds = tagIds - ids, tagCounts = tagCounts - ids)

fun DayLog.count(id: Long): Int = if (id in tagIds) tagCounts[id] ?: 1 else 0

fun CustomCategory.builtInField(): String? = iconKey.removePrefix("builtin:").takeIf {
    iconKey.startsWith("builtin:") && it in ownItemFields
}

/** All entry selection semantics live here, including choices across enum and tag storage. */
object EntrySelection {
    fun builtIn(log: DayLog, field: String, name: String?, tags: List<Tag>, categories: List<CustomCategory>): DayLog {
        val categoryIds = categories.filter { it.builtInField() == field }.map { it.id }.toSet()
        val ownIds = tags.filter { it.categoryId in categoryIds }.map { it.id }.toSet()
        val exclusive = categories.isSingleChoice(field)
        val selected = if (name != null && (exclusive || field in configurableChoiceFields && name == "NONE"))
            log.withoutTags(ownIds) else log
        fun <T> choices(values: Set<T>, value: T?, none: T): Set<T> = when {
            value == null -> emptySet()
            exclusive -> if (values == setOf(value) && log.tagIds.none { it in ownIds }) emptySet() else setOf(value)
            value in values -> values - value
            value == none -> setOf(none)
            else -> values - none + value
        }
        return when (field) {
            "mood" -> selected.copy(mood = name?.let(Mood::valueOf))
            "pain" -> selected.copy(pain = name?.let(Pain::valueOf))
            "sex" -> selected.copy(sex = choices(log.sex, name?.let(Sex::valueOf), Sex.NONE))
            "discharge" -> selected.copy(discharge = choices(log.discharge, name?.let(Discharge::valueOf), Discharge.NONE))
            "flow" -> selected.copy(flow = name?.let(FlowLevel::valueOf))
            "ovulation_test" -> selected.copy(ovulationTest = name?.let(OvulationTest::valueOf))
            "appetite" -> selected.copy(appetite = name?.let(Appetite::valueOf))
            "libido" -> selected.copy(libido = name?.let(Libido::valueOf))
            else -> error("Not a built-in choice field")
        }
    }

    fun tag(log: DayLog, id: Long, tags: List<Tag>, categories: List<CustomCategory>): DayLog {
        val tag = requireNotNull(tags.find { it.id == id })
        val category = categories.find { it.id == tag.categoryId }
        val field = category?.builtInField()
        val exclusive = if (field != null) categories.isSingleChoice(field) else category?.singleChoice == true
        val siblings = if (exclusive) tags.filter { it.categoryId == tag.categoryId }.map { it.id }.toSet() else emptySet()
        val builtInSelected = when (field) {
            "mood" -> log.mood != null
            "pain" -> log.pain != null
            "sex" -> log.sex.isNotEmpty()
            "discharge" -> log.discharge.isNotEmpty()
            else -> false
        }
        if (id in log.tagIds && (!exclusive || !builtInSelected && log.tagIds.count { it in siblings } == 1))
            return log.withoutTags(setOf(id))
        val selected = log.withoutTags(siblings).let { it.copy(tagIds = it.tagIds + id) }
        return when (field) {
            "sex" -> selected.copy(sex = if (exclusive) emptySet() else selected.sex - Sex.NONE)
            "discharge" -> selected.copy(discharge = if (exclusive) emptySet() else selected.discharge - Discharge.NONE)
            in singleChoiceFields -> builtIn(selected, requireNotNull(field), null, tags, categories)
            else -> selected
        }
    }

    /** Sets how many times an item was logged; 0 removes it. */
    fun setCount(log: DayLog, id: Long, times: Int): DayLog {
        val count = times.coerceIn(0, MAX_DAILY_COUNT)
        if (count == 0) return log.withoutTags(setOf(id))
        return log.copy(tagIds = log.tagIds + id, tagCounts = if (count == 1) log.tagCounts - id else log.tagCounts + (id to count))
    }

    fun valid(log: DayLog, tags: List<Tag>, categories: List<CustomCategory>): Boolean {
        if (!log.tagIds.containsAll(log.tagCounts.keys) || log.tagCounts.values.any { it !in 2..MAX_DAILY_COUNT }) return false
        if (Sex.NONE in log.sex && log.sex.size > 1 || Discharge.NONE in log.discharge && log.discharge.size > 1) return false
        // Switching to Pick one preserves existing multi-item diary entries, including built-in categories.
        val selected = tags.filter { it.id in log.tagIds }
        return categories.all { category ->
            val field = category.builtInField()
            val count = selected.count { it.categoryId == category.id }
            val builtIn = when (field) {
                "mood" -> log.mood != null
                "pain" -> log.pain != null
                else -> false
            }
            (!(field in singleChoiceFields) || count <= 1) && !(builtIn && count > 0) &&
                !(count > 0 && (field == "sex" && Sex.NONE in log.sex || field == "discharge" && Discharge.NONE in log.discharge))
        }
    }
}
