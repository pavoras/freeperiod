package org.freeperiod.engine.importing

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

/** A one-entry ZIP like Flo's: deflated, and with ZipCrypto when a password is given. */
internal fun testZip(name: String, content: ByteArray, password: String?): ByteArray {
    val crc = CRC32().apply { update(content) }.value.toInt()
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true).apply { setInput(content); finish() }
    val compressed = ByteArrayOutputStream().apply {
        val chunk = ByteArray(1024)
        while (!deflater.finished()) write(chunk, 0, deflater.deflate(chunk))
    }.toByteArray().also { deflater.end() }
    val data = if (password == null) compressed else {
        val keys = ZipCryptoKeys(password.toCharArray())
        val header = ByteArray(12) { if (it == 11) (crc ushr 24).toByte() else (it * 37).toByte() }
        (header + compressed).map(keys::encrypt).toByteArray()
    }
    val flags = if (password == null) 0x800 else 0x801
    val nameBytes = name.toByteArray()
    fun le(size: Int, fill: ByteBuffer.() -> Unit) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()
    val local = le(30) { putInt(0x04034b50); putShort(20); putShort(flags.toShort()); putShort(8); putInt(0)
        putInt(crc); putInt(data.size); putInt(content.size); putShort(nameBytes.size.toShort()); putShort(0) }
    val central = le(46) { putInt(0x02014b50); putShort(20); putShort(20); putShort(flags.toShort()); putShort(8); putInt(0)
        putInt(crc); putInt(data.size); putInt(content.size); putShort(nameBytes.size.toShort()); putShort(0); putShort(0)
        putShort(0); putShort(0); putInt(0); putInt(0) }
    val centralStart = local.size + nameBytes.size + data.size
    val end = le(22) { putInt(0x06054b50); putShort(0); putShort(0); putShort(1); putShort(1)
        putInt(central.size + nameBytes.size); putInt(centralStart); putShort(0) }
    return local + nameBytes + data + central + nameBytes + end
}
