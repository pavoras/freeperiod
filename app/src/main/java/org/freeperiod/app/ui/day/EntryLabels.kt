package org.freeperiod.app.ui.day

import org.freeperiod.app.R
import org.freeperiod.engine.*

internal fun flowLabel(value: FlowLevel): Int = when (value) {
    FlowLevel.NONE -> R.string.entry_none
    FlowLevel.SPOTTING -> R.string.flow_spotting
    FlowLevel.LIGHT -> R.string.flow_light
    FlowLevel.MEDIUM -> R.string.flow_medium
    FlowLevel.HEAVY -> R.string.flow_heavy
}

internal fun moodLabel(value: Mood): Int = when (value) {
    Mood.GREAT -> R.string.mood_great
    Mood.GOOD -> R.string.mood_good
    Mood.OKAY -> R.string.mood_okay
    Mood.LOW -> R.string.mood_low
    Mood.BAD -> R.string.mood_bad
}

internal fun moodIcon(value: Mood): Int = when (value) {
    Mood.GREAT -> R.drawable.ic_mood_great
    Mood.GOOD -> R.drawable.ic_mood_good
    Mood.OKAY -> R.drawable.ic_mood_okay
    Mood.LOW -> R.drawable.ic_mood_low
    Mood.BAD -> R.drawable.ic_mood_bad
}

internal fun painLabel(value: Pain): Int = when (value) {
    Pain.NONE -> R.string.entry_none
    Pain.MILD -> R.string.pain_mild
    Pain.MODERATE -> R.string.pain_moderate
    Pain.SEVERE -> R.string.pain_severe
}

internal fun sexLabel(value: Sex): Int = when (value) {
    Sex.NONE -> R.string.sex_none
    Sex.PROTECTED -> R.string.sex_protected
    Sex.UNPROTECTED -> R.string.sex_unprotected
}

internal fun dischargeLabel(value: Discharge): Int = when (value) {
    Discharge.NONE -> R.string.discharge_none
    Discharge.STICKY -> R.string.discharge_sticky
    Discharge.CREAMY -> R.string.discharge_creamy
    Discharge.WATERY -> R.string.discharge_watery
    Discharge.EGG_WHITE -> R.string.discharge_egg_white
    Discharge.UNUSUAL -> R.string.discharge_unusual
}

internal fun symptomLabel(value: Symptom): Int = when (value) {
    Symptom.CRAMPS -> R.string.symptom_cramps
    Symptom.HEADACHE -> R.string.symptom_headache
    Symptom.BACKACHE -> R.string.symptom_backache
    Symptom.BLOATING -> R.string.symptom_bloating
    Symptom.BREAST_TENDERNESS -> R.string.symptom_breast_tenderness
    Symptom.ACNE -> R.string.symptom_acne
    Symptom.FATIGUE -> R.string.symptom_fatigue
    Symptom.NAUSEA -> R.string.symptom_nausea
    Symptom.CRAVINGS -> R.string.symptom_cravings
    Symptom.INSOMNIA -> R.string.symptom_insomnia
    Symptom.DIGESTION -> R.string.symptom_digestion
    Symptom.ANXIOUS -> R.string.symptom_anxious
    Symptom.IRRITABLE -> R.string.symptom_irritable
    Symptom.SAD -> R.string.symptom_sad
    Symptom.ENERGETIC -> R.string.symptom_energetic
}

internal fun symptomIcon(value: Symptom): Int = when (value) {
    Symptom.CRAMPS -> R.drawable.ic_symptom_cramps
    Symptom.HEADACHE -> R.drawable.ic_symptom_headache
    Symptom.BACKACHE -> R.drawable.ic_symptom_backache
    Symptom.BLOATING -> R.drawable.ic_symptom_bloating
    Symptom.BREAST_TENDERNESS -> R.drawable.ic_symptom_breast_tenderness
    Symptom.ACNE -> R.drawable.ic_symptom_acne
    Symptom.FATIGUE -> R.drawable.ic_symptom_fatigue
    Symptom.NAUSEA -> R.drawable.ic_symptom_nausea
    Symptom.CRAVINGS -> R.drawable.ic_symptom_cravings
    Symptom.INSOMNIA -> R.drawable.ic_symptom_insomnia
    Symptom.DIGESTION -> R.drawable.ic_symptom_digestion
    Symptom.ANXIOUS -> R.drawable.ic_symptom_anxious
    Symptom.IRRITABLE -> R.drawable.ic_symptom_irritable
    Symptom.SAD -> R.drawable.ic_symptom_sad
    Symptom.ENERGETIC -> R.drawable.ic_symptom_energetic
}
