package org.freeperiod.engine.backup

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.freeperiod.engine.Period
import org.freeperiod.engine.PeriodRules

/** A complete decode outcome; failures never expose partially restored data. */
sealed interface DecodeResult {
    /** An authenticated, fully validated payload ready for restore confirmation. */
    data class Ok(val data: BackupData) : DecodeResult
    /** The file does not contain a complete FreePeriod backup header. */
    data object NotABackup : DecodeResult
    /** The header format or payload schema version is unsupported. */
    data object UnsupportedVersion : DecodeResult
    /** Authentication failed because of the password or damaged ciphertext/header. */
    data object WrongPasswordOrCorrupt : DecodeResult
    /** Bounds, JSON, relationships, or domain rules are invalid. */
    data object InvalidContent : DecodeResult
}

/** Synchronous JCA backup encryption and validation; callers run it on Dispatchers.Default. */
object BackupCodec {
    /** Default PBKDF2 work factor for exported backups. */
    const val ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val HEADER_BYTES = 37
    private const val TAG_BYTES = 16
    private const val MAX_FILE_BYTES = 20_000_000
    private const val MAX_JSON_BYTES = 50_000_000
    private val magic = "FPBK".toByteArray(UTF_8)
    private val json = Json { encodeDefaults = true }

    /** Encrypts a payload with fresh salt and nonce, preserving the caller's password array. */
    fun encode(
        data: BackupData,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid KDF iterations" }
        val plaintext = json.encodeToString(data).toByteArray(UTF_8)
        try {
            require(plaintext.size <= MAX_JSON_BYTES && plaintext.size <= MAX_FILE_BYTES - HEADER_BYTES - TAG_BYTES) {
                "Backup exceeds size limit"
            }
            val salt = ByteArray(16).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val header = ByteBuffer.allocate(HEADER_BYTES).put(magic).put(1.toByte())
                .putInt(iterations).put(salt).put(nonce).array()
            return header + crypt(Cipher.ENCRYPT_MODE, plaintext, password, iterations, salt, nonce, header)
        } finally {
            plaintext.fill(0)
        }
    }

    /** Decodes using the system calendar date; use the clock overload for injected app time. */
    fun decode(bytes: ByteArray, password: CharArray): DecodeResult = decode(bytes, password) { LocalDate.now() }

    /** Decodes and validates against the date supplied by the caller's clock. */
    fun decode(bytes: ByteArray, password: CharArray, clock: () -> LocalDate): DecodeResult =
        decode(bytes, password, clock())

    internal fun decode(
        bytes: ByteArray,
        password: CharArray,
        today: LocalDate,
        maxFileBytes: Int = MAX_FILE_BYTES,
        maxJsonBytes: Int = MAX_JSON_BYTES,
    ): DecodeResult {
        if (bytes.size > maxFileBytes) return DecodeResult.InvalidContent
        if (bytes.size < HEADER_BYTES || !bytes.copyOfRange(0, 4).contentEquals(magic)) return DecodeResult.NotABackup
        val header = bytes.copyOfRange(0, HEADER_BYTES)
        val fields = ByteBuffer.wrap(header)
        fields.position(4)
        if (fields.get().toInt() != 1) return DecodeResult.UnsupportedVersion
        val iterations = fields.int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS || bytes.size < HEADER_BYTES + TAG_BYTES) return DecodeResult.InvalidContent
        val salt = ByteArray(16).also(fields::get)
        val nonce = ByteArray(12).also(fields::get)
        val plaintext = try {
            crypt(Cipher.DECRYPT_MODE, bytes.copyOfRange(HEADER_BYTES, bytes.size), password, iterations, salt, nonce, header)
        } catch (_: GeneralSecurityException) {
            return DecodeResult.WrongPasswordOrCorrupt
        }
        try {
            if (plaintext.size > maxJsonBytes) return DecodeResult.InvalidContent
            // GCM authentication has succeeded before any UTF-8 or JSON parsing.
            val text = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString()
            val data = json.decodeFromString<BackupData>(text)
            if (data.schemaVersion != 1) return DecodeResult.UnsupportedVersion
            return if (valid(data, today)) DecodeResult.Ok(data) else DecodeResult.InvalidContent
        } catch (_: SerializationException) {
            return DecodeResult.InvalidContent
        } catch (_: DateTimeException) {
            return DecodeResult.InvalidContent
        } catch (_: CharacterCodingException) {
            return DecodeResult.InvalidContent
        } catch (_: IllegalArgumentException) {
            return DecodeResult.InvalidContent
        } finally {
            plaintext.fill(0)
        }
    }

    private fun valid(data: BackupData, today: LocalDate): Boolean {
        if (data.settings.typicalCycleLength?.let { it !in 15..90 } == true) return false
        if (data.periods.map { it.id }.toSet().size != data.periods.size) return false
        if (data.dayLogs.map { it.date }.toSet().size != data.dayLogs.size) return false
        val tagIds = data.tags.map { it.id }.toSet()
        if (tagIds.size != data.tags.size) return false
        if (data.tags.map { it.name.lowercase(Locale.ROOT) }.toSet().size != data.tags.size) return false
        if (data.dayLogs.any { !tagIds.containsAll(it.tagIds) }) return false
        var previous: Period? = null
        for (period in data.periods.sortedBy { it.start }) {
            if (PeriodRules.validate(listOfNotNull(previous), period, today) != null) return false
            previous = period
        }
        return true
    }

    private fun crypt(
        mode: Int, input: ByteArray, password: CharArray, iterations: Int,
        salt: ByteArray, nonce: ByteArray, header: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return cipher.doFinal(input)
        } finally {
            key.fill(0)
        }
    }
}
