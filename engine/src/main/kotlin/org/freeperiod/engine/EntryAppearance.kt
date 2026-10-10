package org.freeperiod.engine

data class EntryAppearance(val label: String, val iconKey: String)

/** Callers provide the translated default. Own rows keep their name and icon in every language. */
fun resolveEntryAppearance(key: String, defaultLabel: String, defaultIconKey: String,
    overrides: List<UiOverride>, tag: Tag? = null): EntryAppearance {
    if (tag != null) return EntryAppearance(tag.name, tag.iconKey)
    val override = overrides.find { it.key == key }
    return EntryAppearance(override?.label ?: defaultLabel, override?.iconKey ?: defaultIconKey)
}
