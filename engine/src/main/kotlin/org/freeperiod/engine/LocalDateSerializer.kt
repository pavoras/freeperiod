package org.freeperiod.engine

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Serializes calendar dates as ISO-8601 strings without a time zone. */
object LocalDateSerializer : KSerializer<LocalDate> {
    /** Describes the ISO date string representation. */
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    /** Writes a date as an ISO string. */
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    /** Reads an ISO date string. */
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
