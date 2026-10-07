package org.freeperiod.engine.backup

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val password = "test password".toCharArray()
    private val today = LocalDate.of(2026, 3, 11)
    private val data = BackupData(
        periods = listOf(
            Period(1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5), CycleUse.EXCLUDE),
            Period(2, LocalDate.of(2026, 3, 1), null, CycleUse.INCLUDE),
        ),
        dayLogs = listOf(DayLog(
            LocalDate.of(2026, 3, 1), FlowLevel.HEAVY, Mood.LOW,
            setOf(Symptom.CRAMPS, Symptom.FATIGUE), Pain.MODERATE, Sex.PROTECTED,
            Discharge.CREAMY, "A note: Ã¤, æ°´", setOf(1, 2),
        )),
        tags = listOf(Tag(1, "Work"), Tag(2, "Hike", archived = true)),
        settings = BackupSettings(30, false),
    )

    private fun encode(input: BackupData = data) = BackupCodec.encode(input, password, iterations = 100_000)
    private fun decode(bytes: ByteArray) = BackupCodec.decode(bytes, password, today)
    private fun assertInvalid(input: BackupData) = assertEquals(DecodeResult.InvalidContent, decode(encode(input)))

    @Test fun roundTripEqualsInput() {
        val bytes = BackupCodec.encode(data, password)
        assertEquals(600_000, BackupCodec.ITERATIONS)
        assertEquals(600_000, ByteBuffer.wrap(bytes, 5, 4).int)
        assertEquals(DecodeResult.Ok(data), decode(bytes))
        assertEquals(DecodeResult.Ok(data), BackupCodec.decode(bytes, password) { today })
    }

    @Test fun wrongPasswordFailsWithoutData() {
        val result = BackupCodec.decode(encode(), "wrong password".toCharArray(), today)
        assertEquals(DecodeResult.WrongPasswordOrCorrupt, result)
        assertFalse(result is DecodeResult.Ok)
    }

    @Test fun flippedByteFails() {
        val bytes = encode()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertEquals(DecodeResult.WrongPasswordOrCorrupt, decode(bytes))
    }

    @Test fun randomBytesAreNotABackup() {
        assertEquals(DecodeResult.NotABackup, decode(ByteArray(60) { it.toByte() }))
        assertEquals(DecodeResult.NotABackup, decode(byteArrayOf()))
        assertEquals(DecodeResult.NotABackup, decode("FPBK".toByteArray(UTF_8)))
    }

    @Test fun futureVersionUnsupported() {
        val bytes = encode()
        bytes[4] = 2
        assertEquals(DecodeResult.UnsupportedVersion, decode(bytes))
    }

    @Test fun lowIterationsRejected() {
        val bytes = encode()
        ByteBuffer.wrap(bytes).putInt(5, 99_999)
        assertEquals(DecodeResult.InvalidContent, decode(bytes))
    }

    @Test fun iterationsAboveMaxRejected() {
        val bytes = encode()
        ByteBuffer.wrap(bytes).putInt(5, 10_000_001)
        assertEquals(DecodeResult.InvalidContent, decode(bytes))
    }

    @Test fun oversizedFileRejected() {
        assertEquals(DecodeResult.InvalidContent, BackupCodec.decode(ByteArray(65), password, today, maxFileBytes = 64))
    }

    @Test fun overlappingPeriodsRejected() {
        assertInvalid(data.copy(periods = listOf(data.periods[0], data.periods[1].copy(start = LocalDate.of(2026, 2, 5)))))
    }

    @Test fun duplicateDayDatesRejected() {
        assertInvalid(data.copy(dayLogs = data.dayLogs + data.dayLogs.single()))
    }

    @Test fun unknownTagReferenceRejected() {
        assertInvalid(data.copy(dayLogs = listOf(data.dayLogs.single().copy(tagIds = setOf(99)))))
    }

    @Test fun typicalLengthOutOfRangeRejected() {
        listOf(14, 91).forEach { assertInvalid(data.copy(settings = BackupSettings(it, false))) }
        listOf(null, 15, 90).forEach {
            val input = data.copy(settings = BackupSettings(it, true))
            assertEquals(DecodeResult.Ok(input), decode(encode(input)))
        }
    }

    @Test fun duplicatePeriodIdsRejected() {
        assertInvalid(data.copy(periods = data.periods.map { it.copy(id = 1) }))
    }

    @Test fun duplicateTagIdsAndNamesRejected() {
        assertInvalid(data.copy(tags = listOf(Tag(1, "Work"), Tag(1, "Hike"))))
        assertInvalid(data.copy(tags = listOf(Tag(1, "Work"), Tag(2, "work"))))
    }

    @Test fun futureAndReversedPeriodDatesRejected() {
        assertInvalid(data.copy(periods = listOf(data.periods[1].copy(start = today.plusDays(1)))))
        assertInvalid(data.copy(periods = listOf(data.periods[1].copy(end = today.plusDays(1)))))
        assertInvalid(data.copy(periods = listOf(data.periods[1].copy(end = data.periods[1].start.minusDays(1)))))
    }

    @Test fun multipleOngoingPeriodsRejected() {
        assertInvalid(data.copy(periods = data.periods.map { it.copy(end = null) }))
    }

    @Test fun schemaVersionUnsupported() {
        assertEquals(DecodeResult.UnsupportedVersion, decode(encode(data.copy(schemaVersion = 2))))
    }

    @Test fun headerIsAuthenticated() {
        listOf(9, 25).forEach { index ->
            val bytes = encode()
            bytes[index] = (bytes[index].toInt() xor 1).toByte()
            assertEquals(DecodeResult.WrongPasswordOrCorrupt, decode(bytes))
        }
        val bytes = encode()
        ByteBuffer.wrap(bytes).putInt(5, 100_001)
        assertEquals(DecodeResult.WrongPasswordOrCorrupt, decode(bytes))
    }

    @Test fun eachExportHasFreshSaltAndNonce() {
        val first = encode()
        val second = encode()
        assertEquals("FPBK", String(first, 0, 4, UTF_8))
        assertEquals(1, first[4].toInt())
        assertFalse(first.copyOfRange(9, 25).contentEquals(second.copyOfRange(9, 25)))
        assertFalse(first.copyOfRange(25, 37).contentEquals(second.copyOfRange(25, 37)))
        assertEquals(DecodeResult.Ok(data), decode(second))
    }

    @Test fun authenticatedInvalidJsonRejected() {
        assertEquals(DecodeResult.InvalidContent, decode(rawBackup("{")))
        val json = Json.encodeToString(data)
        assertEquals(DecodeResult.InvalidContent, decode(rawBackup(json.replace("2026-03-01", "2026-02-30"))))
        assertEquals(DecodeResult.InvalidContent, decode(rawBackup(json.replace("HEAVY", "UNKNOWN"))))
    }

    @Test fun oversizedJsonRejectedAfterAuthentication() {
        val bytes = encode()
        assertEquals(DecodeResult.InvalidContent, BackupCodec.decode(bytes, password, today, maxJsonBytes = 8))
    }

    @Test fun modelSerializesIsoDatesAndStableEnumNames() {
        val json = Json.encodeToString(data)
        assertTrue(json.contains("\"start\":\"2026-02-01\""))
        assertTrue(json.contains("\"cycleUse\":\"EXCLUDE\""))
        assertTrue(json.contains("\"flow\":\"HEAVY\""))
        assertEquals(data, Json.decodeFromString<BackupData>(json))
    }

    @Test fun encodeRejectsInvalidIterationBoundsAndPreservesPassword() {
        val copy = password.copyOf()
        listOf(99_999, 10_000_001).forEach { iterations ->
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(data, password, iterations = iterations) }
        }
        decode(encode())
        assertArrayEquals(copy, password)
    }

    @Test fun truncatedCiphertextFailsWithoutData() {
        val bytes = encode()
        assertEquals(DecodeResult.WrongPasswordOrCorrupt, decode(bytes.copyOf(bytes.size - 1)))
        assertEquals(DecodeResult.InvalidContent, decode(bytes.copyOf(37)))
    }

    // Build independently encrypted payloads so JSON validation is tested after valid GCM authentication.
    private fun rawBackup(json: String): ByteArray {
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it + 16).toByte() }
        val header = ByteBuffer.allocate(37).put("FPBK".toByteArray(UTF_8)).put(1.toByte())
            .putInt(100_000).put(salt).put(nonce).array()
        val spec = PBEKeySpec(password, salt, 100_000, 256)
        val keyBytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(json.toByteArray(UTF_8))
        } finally { keyBytes.fill(0) }
    }
}

