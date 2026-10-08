package org.freeperiod.engine.backup

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.time.LocalTime
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
        assertEquals(DecodeResult.UnsupportedVersion, decode(rawBackup("""{"schemaVersion":3,"unknownFutureField":true}""")))
    }

    @Test fun decodesSchema1() {
        val legacy = """{"schemaVersion":1,"periods":[{"id":1,"start":"2026-02-01","end":"2026-02-05","cycleUse":"EXCLUDE"}],"dayLogs":[{"date":"2026-02-01","flow":"LIGHT","tagIds":[7],"note":"${"x".repeat(2001)}"}],"tags":[{"id":7,"name":"Walk","archived":true}],"settings":{"typicalCycleLength":28,"predictionsPaused":false}}"""
        val result = decode(rawBackup(legacy)) as DecodeResult.Ok
        assertEquals(2, result.data.schemaVersion)
        assertEquals(listOf(Tag(7, "Walk", true)), result.data.tags)
        assertEquals(Situation(), result.data.situation)
        assertTrue(result.data.situation.fertileWindowEnabled)
        assertTrue(result.data.customCategories.isEmpty())
        assertEquals(2001, result.data.dayLogs.single().note!!.length)
        assertNull(result.data.dayLogs.single().ovulationTest)
    }

    @Test fun missingV2PreferenceDefaultsOnAndExplicitFalseSurvivesRestore() {
        val json = Json { encodeDefaults = true }.encodeToString(data)
        val withoutPreference = json.replace(Regex(",?\"fertileWindowEnabled\":(?:true|false)"), "")
        val restored = decode(rawBackup(withoutPreference)) as DecodeResult.Ok
        assertTrue(restored.data.situation.fertileWindowEnabled)
        val disabled = data.copy(situation = Situation(fertileWindowEnabled = false))
        assertEquals(DecodeResult.Ok(disabled), decode(encode(disabled)))
    }

    @Test fun roundTripV2() {
        val recurrences = listOf(Recurrence.Daily, Recurrence.EveryNDays(3, today),
            Recurrence.Weekly(java.time.DayOfWeek.MONDAY), Recurrence.MonthlyOnDay(31),
            Recurrence.EveryNMonths(3, LocalDate.of(2026, 1, 31)), Recurrence.Once(today.plusDays(90)))
        val input = data.copy(
            situation = Situation(LifePhase.PERIMENOPAUSE, Method.PILL_COMBINED, PillSchedule(today, 24, 4), true),
            customCategories = listOf(CustomCategory(9, "Activities", "leaf", 3, true)),
            tags = listOf(Tag(1, "Work", categoryId = 9, iconKey = "leaf"), Tag(2, "Hike", true)),
            overrides = listOf(UiOverride("category:flow", true, 2), UiOverride("item:symptoms:HOT_FLUSHES", false, 1),
                UiOverride("customCategory:9", false, 4), UiOverride("tag:1", true, 5)),
            reminders = recurrences.mapIndexed { index, recurrence ->
                Reminder(index + 1L, ReminderKind.CUSTOM, "My reminder", recurrence, LocalTime.of(9, 30), index % 2 == 0)
            } + Reminder(7, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(8, 0), true, 2),
            hintDismissals = setOf(1),
            dayLogs = listOf(data.dayLogs.single().copy(ovulationTest = OvulationTest.POSITIVE,
                symptoms = setOf(Symptom.HOT_FLUSHES, Symptom.NIGHT_SWEATS, Symptom.BRAIN_FOG, Symptom.JOINT_PAIN))),
        )
        assertEquals(DecodeResult.Ok(input), decode(encode(input)))
    }

    @Test fun invalidRecurrenceAndPillConfigRejected() {
        val input = data.copy(situation = Situation(method = Method.PILL_COMBINED, pill = PillSchedule(today, 21, 7)),
            reminders = listOf(Reminder(1, ReminderKind.CUSTOM, null, Recurrence.EveryNDays(3, today), LocalTime.NOON, true)))
        val text = Json { encodeDefaults = true }.encodeToString(input)
        listOf(text.replace("\"n\":3", "\"n\":0"), text.replace("\"n\":3", "\"n\":1000"),
            text.replace("\"activeDays\":21", "\"activeDays\":0"), text.replace("\"breakDays\":7", "\"breakDays\":31"),
            text.replace(",\"breakDays\":7", ""), text.replace("\"activeDays\":21,", "")).forEach {
            assertEquals(DecodeResult.InvalidContent, decode(rawBackup(it)))
        }
    }

    @Test fun validatesV2IdsReferencesAndKeys() {
        assertInvalid(data.copy(customCategories = listOf(CustomCategory(1, "A", "tag", 0, false), CustomCategory(1, "B", "tag", 1, false))))
        assertInvalid(data.copy(tags = data.tags.map { it.copy(categoryId = 99) }))
        assertInvalid(data.copy(tags = data.tags.map { it.copy(id = -it.id) }))
        assertInvalid(data.copy(hintDismissals = setOf(99)))
        assertInvalid(data.copy(overrides = listOf(UiOverride("category:unknown", false, 0))))
        assertInvalid(data.copy(overrides = listOf(UiOverride("customCategory:99", false, 0))))
        assertInvalid(data.copy(overrides = listOf(UiOverride("tag:99", false, 0))))
        assertInvalid(data.copy(overrides = List(2) { UiOverride("category:flow", false, it) }))
        val reminder = Reminder(1, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.NOON, true, 2)
        assertInvalid(data.copy(reminders = listOf(reminder, reminder)))
        assertInvalid(data.copy(reminders = listOf(reminder.copy(daysBefore = null))))
        assertInvalid(data.copy(reminders = listOf(reminder.copy(daysBefore = -1))))
        assertInvalid(data.copy(reminders = listOf(reminder.copy(kind = ReminderKind.CUSTOM))))
        assertEquals(DecodeResult.Ok(data.copy(tags = listOf(Tag(1, "Walk"), Tag(2, "Walk", categoryId = 9)),
            customCategories = listOf(CustomCategory(9, "Activities", "tag", 0, false)))),
            decode(encode(data.copy(tags = listOf(Tag(1, "Walk"), Tag(2, "Walk", categoryId = 9)),
                customCategories = listOf(CustomCategory(9, "Activities", "tag", 0, false))))))
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
