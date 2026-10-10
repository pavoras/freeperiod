package org.freeperiod.engine

internal fun builtInNames(field: String): Set<String> = when (field) {
    "mood" -> Mood.entries.map { it.name }
    "pain" -> Pain.entries.map { it.name }
    "symptoms" -> Symptom.entries.map { it.name }
    "sex" -> Sex.entries.map { it.name }
    "discharge" -> Discharge.entries.map { it.name }
    else -> emptyList()
}.toSet()

/** Escape list separators and enum-key collisions while leaving ordinary user names readable. */
internal fun csvItemName(name: String, reserved: Set<String> = emptySet()): String {
    val escaped = name.replace("%", "%25").replace(";", "%3B").replace(":", "%3A")
    return if (reserved.any { it.equals(name, ignoreCase = true) })
        "%" + name.first().code.toString(16).uppercase() + escaped.drop(1) else escaped
}

internal fun csvItemNameDecoded(name: String): String = Regex("%([0-9A-Fa-f]{2})").replace(name) {
    it.groupValues[1].toInt(16).toChar().toString()
}
