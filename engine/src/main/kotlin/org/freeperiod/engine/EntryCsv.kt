package org.freeperiod.engine

internal fun builtInNames(field: String): Set<String> = when (field) {
    "mood" -> Mood.entries.map { it.name }
    "pain" -> Pain.entries.map { it.name }
    "symptoms" -> Symptom.entries.map { it.name }
    "sex" -> Sex.entries.map { it.name }
    "discharge" -> Discharge.entries.map { it.name }
    "medication" -> emptyList()
    else -> emptyList()
}.toSet()

/** Escape list separators and enum-key collisions while leaving ordinary user names readable. */
internal fun csvItemName(name: String, reserved: Set<String> = emptySet()): String {
    val escaped = name.replace("%", "%25").replace(";", "%3B").replace(":", "%3A")
    return if (reserved.any { it.equals(name, ignoreCase = true) })
        "%" + name.first().code.toString(16).uppercase() + escaped.drop(1) else escaped
}

/** An item name, followed by ":count" when it was logged more than once that day. */
internal fun csvCountedName(name: String, count: Int, reserved: Set<String> = emptySet()): String =
    csvItemName(name, reserved) + if (count > 1) ":$count" else ""

internal fun csvItemNameDecoded(name: String): String = Regex("%([0-9A-Fa-f]{2})").replace(name) {
    it.groupValues[1].toInt(16).toChar().toString()
}
