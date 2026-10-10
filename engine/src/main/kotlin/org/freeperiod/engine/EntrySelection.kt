package org.freeperiod.engine

/** Marker categories keep own items in tags without changing the stored enum fields. */
val ownItemFields = setOf("mood", "pain", "symptoms", "sex", "discharge")
val singleChoiceFields = setOf("mood", "pain", "sex", "discharge")

fun CustomCategory.builtInField(): String? = iconKey.removePrefix("builtin:").takeIf {
    iconKey.startsWith("builtin:") && it in ownItemFields
}

/** All entry selection semantics live here, including choices across enum and tag storage. */
object EntrySelection {
    fun builtIn(log: DayLog, field: String, name: String?, tags: List<Tag>, categories: List<CustomCategory>): DayLog {
        val categoryIds = categories.filter { it.builtInField() == field }.map { it.id }.toSet()
        val ownIds = tags.filter { it.categoryId in categoryIds }.map { it.id }.toSet()
        val selected = if (name != null && field in singleChoiceFields) log.copy(tagIds = log.tagIds - ownIds) else log
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
        if (id in log.tagIds) return log.copy(tagIds = log.tagIds - id)
        val category = categories.find { it.id == tag.categoryId }
        val field = category?.builtInField()
        val exclusive = category?.singleChoice == true || field in singleChoiceFields
        val siblings = if (exclusive) tags.filter { it.categoryId == tag.categoryId }.map { it.id }.toSet() else emptySet()
        val selected = log.copy(tagIds = (log.tagIds - siblings) + id)
        return if (field in singleChoiceFields) builtIn(selected, requireNotNull(field), null, tags, categories) else selected
    }

    fun valid(log: DayLog, tags: List<Tag>, categories: List<CustomCategory>): Boolean {
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
