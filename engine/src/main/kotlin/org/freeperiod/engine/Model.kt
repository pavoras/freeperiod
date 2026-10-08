@file:UseSerializers(LocalDateSerializer::class)

package org.freeperiod.engine

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/** Controls whether the cycle beginning at a period contributes to predictions. */
@Serializable
enum class CycleUse { AUTO, INCLUDE, EXCLUDE }

/** A logged period with inclusive boundaries and a null end while ongoing. */
@Serializable
data class Period(val id: Long, val start: LocalDate, val end: LocalDate?, val cycleUse: CycleUse = CycleUse.AUTO)

/** A custom tag retained in history even when archived. */
@Serializable
data class Tag(
    val id: Long,
    val name: String,
    val archived: Boolean = false,
    val categoryId: Long? = null,
    val iconKey: String = "tag",
)

/** Custom multi-select category; archived entries remain available in history. */
@Serializable
data class CustomCategory(val id: Long, val name: String, val iconKey: String, val sortOrder: Int, val archived: Boolean)

/** Stable keys: category:<field>, item:<field>:<ENUM>, customCategory:<id>, tag:<id>. */
@Serializable
data class UiOverride(val key: String, val hidden: Boolean, val sortOrder: Int)

/** Optional observations for one calendar day; null means not logged. */
@Serializable
data class DayLog(
    val date: LocalDate,
    val flow: FlowLevel? = null,
    val mood: Mood? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val pain: Pain? = null,
    val sex: Sex? = null,
    val discharge: Discharge? = null,
    val note: String? = null,
    val tagIds: Set<Long> = emptySet(),
    val ovulationTest: OvulationTest? = null,
) {
    /** Returns true when all observations are null or empty, preserving explicit NONE values. */
    fun isEmpty(): Boolean = flow == null && mood == null && symptoms.isEmpty() && pain == null &&
        sex == null && discharge == null && note.isNullOrEmpty() && tagIds.isEmpty() && ovulationTest == null
}

/** Logged ovulation test result, distinct from no observation. */
@Serializable
enum class OvulationTest { NEGATIVE, POSITIVE }

/** Logged flow levels, distinct from an unlogged value. */
@Serializable
enum class FlowLevel { NONE, SPOTTING, LIGHT, MEDIUM, HEAVY }

/** Logged mood levels. */
@Serializable
enum class Mood { GREAT, GOOD, OKAY, LOW, BAD }

/** Logged pain levels. */
@Serializable
enum class Pain { NONE, MILD, MODERATE, SEVERE }

/** Logged sexual activity categories. */
@Serializable
enum class Sex { NONE, PROTECTED, UNPROTECTED }

/** Logged discharge categories. */
@Serializable
enum class Discharge { NONE, STICKY, CREAMY, WATERY, EGG_WHITE, UNUSUAL }

/** Stable symptom names used in logs, backups, and exports. */
@Serializable
enum class Symptom {
    CRAMPS, HEADACHE, BACKACHE, BLOATING, BREAST_TENDERNESS, ACNE, FATIGUE, NAUSEA,
    CRAVINGS, INSOMNIA, DIGESTION, ANXIOUS, IRRITABLE, SAD, ENERGETIC,
    HOT_FLUSHES, NIGHT_SWEATS, BRAIN_FOG, JOINT_PAIN,
}

/** A completed start-to-start interval with its prediction eligibility. */
@Serializable
data class Cycle(
    val startPeriodId: Long,
    val start: LocalDate,
    val nextStart: LocalDate,
    val length: Int,
    val periodLength: Int?,
    val eligible: Boolean,
    val ineligibleReason: IneligibleReason?,
)

/** Explains why a completed cycle does not contribute to predictions. */
@Serializable
enum class IneligibleReason { EXCLUDED_BY_USER, TOO_SHORT, TOO_LONG }

/** Validation failures for creating or editing a period. */
@Serializable
enum class PeriodError { OVERLAP, END_BEFORE_START, START_IN_FUTURE, END_IN_FUTURE }
