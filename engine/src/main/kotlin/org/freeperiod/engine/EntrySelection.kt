package org.freeperiod.engine

/** Marker categories keep own items in tags without changing the stored enum fields. */
val ownItemFields = setOf("mood", "pain", "symptoms", "sex", "discharge", "medication")
val singleChoiceFields = setOf("mood", "pain", "sex", "discharge")
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
        val selected = if (name != null && field in singleChoiceFields) log.withoutTags(ownIds) else log
        return when (field) {
            "mood" -> selected.copy(mood = name?.let(Mood::valueOf))
            "pain" -> selected.copy(pain = name?.let(Pain::valueOf))
            "sex" -> selected.copy(sex = name?.let(Sex::valueOf))
            "discharge" -> selected.copy(discharge = name?.let(Discharge::valueOf))
            "flow" -> selected.copy(flow = name?.let(FlowLevel::valueOf))
            "ovulation_test" -> selected.copy(ovulationTest = name?.let(OvulationTest::valueOf))
            else -> error("Not a single-choice field")
        }
    }

    fun tag(log: DayLog, id: Long, tags: List<Tag>, categories: List<CustomCategory>): DayLog {
        val tag = requireNotNull(tags.find { it.id == id })
        if (id in log.tagIds) return log.withoutTags(setOf(id))
        val category = categories.find { it.id == tag.categoryId }
        val field = category?.builtInField()
        val exclusive = category?.singleChoice == true || field in singleChoiceFields
        val siblings = if (exclusive) tags.filter { it.categoryId == tag.categoryId }.map { it.id }.toSet() else emptySet()
        val selected = log.withoutTags(siblings).let { it.copy(tagIds = it.tagIds + id) }
        return if (field in singleChoiceFields) builtIn(selected, requireNotNull(field), null, tags, categories) else selected
    }

    /** Sets how many times an item was logged; 0 removes it. */
    fun setCount(log: DayLog, id: Long, times: Int): DayLog {
        val count = times.coerceIn(0, MAX_DAILY_COUNT)
        if (count == 0) return log.withoutTags(setOf(id))
        return log.copy(tagIds = log.tagIds + id, tagCounts = if (count == 1) log.tagCounts - id else log.tagCounts + (id to count))
    }

    fun valid(log: DayLog, tags: List<Tag>, categories: List<CustomCategory>): Boolean {
        if (!log.tagIds.containsAll(log.tagCounts.keys) || log.tagCounts.values.any { it !in 2..MAX_DAILY_COUNT }) return false
        // Switching a custom category to Pick one preserves existing multi-item diary entries.
        val selected = tags.filter { it.id in log.tagIds }
        return categories.all { category ->
            val field = category.builtInField()
            val count = selected.count { it.categoryId == category.id }
            val builtIn = when (field) {
                "mood" -> log.mood != null
                "pain" -> log.pain != null
                "sex" -> log.sex != null
                "discharge" -> log.discharge != null
                else -> false
            }
            (!(field in singleChoiceFields) || count <= 1) && !(builtIn && count > 0)
        }
    }
}
