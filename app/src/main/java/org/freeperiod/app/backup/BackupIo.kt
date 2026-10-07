package org.freeperiod.app.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DocumentIo {
    suspend fun read(uri: Uri): ByteArray
    suspend fun write(uri: Uri, bytes: ByteArray)
}

class BackupTooLargeException : IOException()

class BackupIo(private val resolver: ContentResolver) : DocumentIo {
    override suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val descriptor = resolver.openAssetFileDescriptor(uri, "r") ?: throw IOException()
        descriptor.use {
            if (it.length > MAX_BACKUP_BYTES) throw BackupTooLargeException()
            it.createInputStream().use { stream -> readBackupStream(stream, it.length) }
        }
    }

    override suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException()
        stream.use { it.write(bytes); it.flush() }
    }
}

internal const val MAX_BACKUP_BYTES = 20_000_000

/** Never reads beyond the cap, including from providers with no reported length. */
internal fun readBackupStream(stream: InputStream, length: Long = -1): ByteArray {
    if (length > MAX_BACKUP_BYTES) throw BackupTooLargeException()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < MAX_BACKUP_BYTES) {
        val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BACKUP_BYTES - output.size()))
        if (count == -1) return output.toByteArray()
        if (count == 0) throw IOException()
        output.write(buffer, 0, count)
    }
    // At the boundary, accept only a provider-confirmed exact size without probing another byte.
    if (length == MAX_BACKUP_BYTES.toLong()) return output.toByteArray()
    throw BackupTooLargeException()
}
