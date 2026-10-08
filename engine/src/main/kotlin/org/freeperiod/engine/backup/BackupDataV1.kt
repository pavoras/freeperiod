@file:kotlinx.serialization.UseSerializers(org.freeperiod.engine.LocalDateSerializer::class)

package org.freeperiod.engine.backup

import java.time.LocalDate
import kotlinx.serialization.Serializable
import org.freeperiod.engine.*

/** Original payload shape, selected only after authentication and schema inspection. */
@Serializable
internal data class BackupDataV1(
    val schemaVersion: Int,
    val periods: List<Period>,
    val dayLogs: List<DayLogV1>,
    val tags: List<TagV1>,
    val settings: BackupSettings,
) {
    fun toV2(): BackupData = BackupData(periods = periods, dayLogs = dayLogs.map { it.toV2() },
        tags = tags.map { Tag(it.id, it.name, it.archived) }, settings = settings)
}

@Serializable
internal data class TagV1(val id: Long, val name: String, val archived: Boolean = false)

@Serializable
internal data class DayLogV1(
    val date: LocalDate,
    val flow: FlowLevel? = null,
    val mood: Mood? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val pain: Pain? = null,
    val sex: Sex? = null,
    val discharge: Discharge? = null,
    val note: String? = null,
    val tagIds: Set<Long> = emptySet(),
) {
    fun toV2(): DayLog = DayLog(date, flow, mood, symptoms, pain, sex, discharge, note, tagIds)
}
