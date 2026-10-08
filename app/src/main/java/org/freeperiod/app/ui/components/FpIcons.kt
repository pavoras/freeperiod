package org.freeperiod.app.ui.components

import org.freeperiod.app.R

/** Stable keys for original, bundled line icons. Never persist drawable IDs. */
object FpIcons {
    val byKey = mapOf("sex" to R.drawable.ic_fp_sex, "discharge" to R.drawable.ic_fp_discharge,
        "tags" to R.drawable.ic_fp_tags, "note" to R.drawable.ic_fp_note,
        "today" to R.drawable.ic_fp_today, "history" to R.drawable.ic_fp_history,
        "settings" to R.drawable.ic_fp_settings, "tag" to R.drawable.ic_fp_tags,
        "cramps" to R.drawable.ic_symptom_cramps, "headache" to R.drawable.ic_symptom_headache,
        "backache" to R.drawable.ic_symptom_backache, "bloating" to R.drawable.ic_symptom_bloating,
        "breast" to R.drawable.ic_symptom_breast_tenderness, "acne" to R.drawable.ic_symptom_acne,
        "fatigue" to R.drawable.ic_symptom_fatigue, "nausea" to R.drawable.ic_symptom_nausea,
        "cravings" to R.drawable.ic_symptom_cravings, "sleep" to R.drawable.ic_symptom_insomnia,
        "digestion" to R.drawable.ic_symptom_digestion, "anxious" to R.drawable.ic_symptom_anxious,
        "irritable" to R.drawable.ic_symptom_irritable, "sad" to R.drawable.ic_symptom_sad,
        "energetic" to R.drawable.ic_symptom_energetic, "warmth" to R.drawable.ic_symptom_hot_flushes,
        "night" to R.drawable.ic_symptom_night_sweats, "focus" to R.drawable.ic_symptom_brain_fog,
        "joint" to R.drawable.ic_symptom_joint_pain)
    val itemKeys = listOf("tag", "sex", "discharge", "note", "today", "cramps", "headache", "backache", "bloating",
        "breast", "acne", "fatigue", "nausea", "cravings", "sleep", "digestion", "anxious", "irritable", "sad",
        "energetic", "warmth", "night", "focus", "joint")
}
