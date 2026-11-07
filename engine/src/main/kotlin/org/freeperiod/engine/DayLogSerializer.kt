@file:kotlinx.serialization.UseSerializers(LocalDateSerializer::class)

package org.freeperiod.engine

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Keep the original scalar keys and add complete selections; absent sets read the legacy value. */
@Serializable
private data class StoredDayLog(
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
    val tagCounts: Map<Long, Int> = emptyMap(),
    val appetite: Appetite? = null,
    val libido: Libido? = null,
    val sexValues: Set<Sex>? = null,
    val dischargeValues: Set<Discharge>? = null,
)

object DayLogSerializer : KSerializer<DayLog> {
    override val descriptor = StoredDayLog.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DayLog) {
        encoder.encodeSerializableValue(StoredDayLog.serializer(), StoredDayLog(
            value.date, value.flow, value.mood, value.symptoms, value.pain,
            Sex.entries.firstOrNull { it in value.sex }, Discharge.entries.firstOrNull { it in value.discharge },
            value.note, value.tagIds, value.ovulationTest, value.tagCounts, value.appetite, value.libido,
            value.sex, value.discharge))
    }

    override fun deserialize(decoder: Decoder): DayLog {
        val stored = decoder.decodeSerializableValue(StoredDayLog.serializer())
        return with(stored) { DayLog(date, flow, mood, symptoms, pain,
            sexValues ?: setOfNotNull(sex), dischargeValues ?: setOfNotNull(discharge),
            note, tagIds, ovulationTest, tagCounts, appetite, libido) }
    }
}
