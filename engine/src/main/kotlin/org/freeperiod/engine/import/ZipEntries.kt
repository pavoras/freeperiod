package org.freeperiod.engine.importing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Inflater

/** Thrown when an encrypted entry is read without a password. */
class ZipPasswordRequired : Exception()

/** Thrown when the password is wrong or the archive is damaged (the two can't be told apart). */
class ZipWrongPassword : Exception()

/**
 * Minimal ZIP reader for exports other apps send by e-mail: stored or deflated entries, optionally with
 * the traditional PKWARE encryption ("ZipCrypto") that Flo's support uses. No other ZIP features.
 */
class ZipEntries(private val bytes: ByteArray) {
    data class Entry(val name: String, internal val flags: Int, internal val method: Int, internal val crc: Int,
        internal val compressed: Int, val size: Int, internal val localOffset: Int) {
        val encrypted get() = flags and 1 != 0
    }

    val entries: List<Entry> = readCentralDirectory()

    fun read(entry: Entry, password: CharArray? = null): ByteArray {
        require(entry.size in 0..MAX_ENTRY) { "Entry too large" }
        val local = buffer(entry.localOffset)
        require(local.getInt(0) == LOCAL_HEADER) { "Not a local header" }
        val start = entry.localOffset + 30 + local.getShort(26).toUShort().toInt() + local.getShort(28).toUShort().toInt()
        require(start >= 0 && start + entry.compressed <= bytes.size) { "Truncated entry" }
        var data = bytes.copyOfRange(start, start + entry.compressed)
        if (entry.encrypted) data = decrypt(data, password ?: throw ZipPasswordRequired(), entry)
        val plain = when (entry.method) {
            0 -> data
            8 -> inflate(data, entry.size)
            else -> error("Unsupported compression")
        }
        if (plain.size != entry.size || CRC32().apply { update(plain) }.value.toInt() != entry.crc) {
            if (entry.encrypted) throw ZipWrongPassword() else error("Damaged entry")
        }
        return plain
    }

    private fun readCentralDirectory(): List<Entry> {
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 22 - 65535)).firstOrNull { buffer(it).getInt(0) == END_RECORD }
            ?: error("Not a ZIP")
        val record = buffer(end)
        val count = record.getShort(10).toUShort().toInt()
        var offset = record.getInt(16)
        return List(count) {
            val header = buffer(offset)
            require(header.getInt(0) == CENTRAL_HEADER) { "Damaged ZIP" }
            val nameLength = header.getShort(28).toUShort().toInt()
            val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)
            val entry = Entry(name, header.getShort(8).toUShort().toInt(), header.getShort(10).toUShort().toInt(),
                header.getInt(16), header.getInt(20), header.getInt(24), header.getInt(42))
            offset += 46 + nameLength + header.getShort(30).toUShort().toInt() + header.getShort(32).toUShort().toInt()
            entry
        }
    }

    private fun buffer(offset: Int): ByteBuffer {
        require(offset in 0..bytes.size - 4) { "Damaged ZIP" }
        return ByteBuffer.wrap(bytes, offset, bytes.size - offset).slice().order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun inflate(data: ByteArray, size: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArray(size)
            var read = 0
            while (read < size && !inflater.finished()) {
                val n = inflater.inflate(out, read, size - read)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                read += n
            }
            return if (read == size) out else out.copyOf(read)
        } catch (_: java.util.zip.DataFormatException) {
            throw ZipWrongPassword()
        } finally {
            inflater.end()
        }
    }

    private fun decrypt(data: ByteArray, password: CharArray, entry: Entry): ByteArray {
        if (data.size < 12) throw ZipWrongPassword()
        val keys = ZipCryptoKeys(password)
        val plain = ByteArray(data.size) { keys.decrypt(data[it]) }
        // The last header byte repeats the high byte of the CRC: a quick check before inflating.
        if (plain[11] != (entry.crc ushr 24).toByte()) throw ZipWrongPassword()
        return plain.copyOfRange(12, plain.size)
    }

    private companion object {
        const val LOCAL_HEADER = 0x04034b50
        const val CENTRAL_HEADER = 0x02014b50
        const val END_RECORD = 0x06054b50
        const val MAX_ENTRY = 32 * 1024 * 1024
    }
}

/** The three-key stream cipher of the traditional PKWARE ZIP encryption. */
internal class ZipCryptoKeys(password: CharArray) {
    private var key0 = 305419896
    private var key1 = 591751049
    private var key2 = 878082192

    init {
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        bytes.forEach { update(it) }
        bytes.fill(0)
    }

    fun decrypt(cipher: Byte): Byte {
        val plain = (cipher.toInt() xor streamByte()).toByte()
        update(plain)
        return plain
    }

    fun encrypt(plain: Byte): Byte {
        val cipher = (plain.toInt() xor streamByte()).toByte()
        update(plain)
        return cipher
    }

    private fun streamByte(): Int {
        val temp = key2 or 2
        return ((temp * (temp xor 1)) ushr 8) and 0xff
    }

    private fun update(byte: Byte) {
        key0 = crc(key0, byte.toInt())
        key1 = (key1 + (key0 and 0xff)) * 134775813 + 1
        key2 = crc(key2, key1 ushr 24)
    }

    private fun crc(value: Int, byte: Int): Int = CRC_TABLE[(value xor byte) and 0xff] xor (value ushr 8)

    private companion object {
        val CRC_TABLE = IntArray(256) { n ->
            var c = n
            repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
            c
        }
    }
}
