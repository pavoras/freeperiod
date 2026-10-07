package org.freeperiod.app.backup

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BackupIoTest {
    @Test fun smallStreamRoundTripsWithoutReportedSize() {
        val bytes = "test backup".toByteArray()
        assertArrayEquals(bytes, readBackupStream(ByteArrayInputStream(bytes)))
    }

    @Test fun oversizedUnknownStreamNeverReadsBeyondTwentyMb() {
        var read = 0
        val input = object : InputStream() {
            override fun read(): Int { read++; return 0 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill(0, offset, offset + length)
                read += length
                return length
            }
        }
        assertThrows(BackupTooLargeException::class.java) { readBackupStream(input) }
        assertEquals(MAX_BACKUP_BYTES, read)
    }

    @Test fun reportedOversizeRejectedBeforeReading() {
        val input = object : InputStream() { override fun read(): Int = error("Must not read") }
        assertThrows(BackupTooLargeException::class.java) { readBackupStream(input, MAX_BACKUP_BYTES + 1L) }
    }

    @Test fun exactLimitAcceptedWhenProviderReportsLength() {
        val bytes = ByteArray(MAX_BACKUP_BYTES)
        assertEquals(MAX_BACKUP_BYTES, readBackupStream(ByteArrayInputStream(bytes), MAX_BACKUP_BYTES.toLong()).size)
    }
}
