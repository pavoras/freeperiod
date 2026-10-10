package org.freeperiod.app.backup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.backup.BackupCodec

enum class AutoBackupInterval(val days: Long) { DAILY(1), WEEKLY(7) }

/** Folders picked in the system folder picker; access survives restarts until released. */
interface FolderAccess {
    /** Keeps access and returns the folder's display name. */
    fun keep(uri: String): String
    fun release(uri: String)
    fun open(uri: String): BackupFolder
}

/** A folder the user picked; the app only writes, lists and removes its own automatic backups there. */
interface BackupFolder {
    suspend fun names(): List<String>
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun delete(name: String)
}

/** Keeps the automatic-backup password unreadable outside this device (Android Keystore in the app). */
interface PasswordSeal {
    fun seal(password: CharArray): String
    /** Null when the key is gone, e.g. after a device transfer. */
    fun open(sealed: String): CharArray?
}

internal const val AUTO_BACKUPS_KEPT = 5
private val autoName = Regex("""freeperiod-auto-\d{4}-\d{2}-\d{2}-\d{4}\.fpbackup""")
private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

internal fun autoBackupName(now: LocalDateTime) = "freeperiod-auto-${now.format(stamp)}.fpbackup"

/** Names sort chronologically; other files in the folder are never touched. */
internal fun autoBackupsToDelete(names: List<String>, keep: Int = AUTO_BACKUPS_KEPT): List<String> =
    names.filter(autoName::matches).sortedDescending().drop(keep)

class AutoBackup(
    private val repository: Repository,
    private val settings: SettingsStore,
    private val seal: PasswordSeal,
    private val folders: FolderAccess,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    private val running = Mutex()

    /** Writes one encrypted backup and keeps the newest five; records the outcome for the backup screen. */
    suspend fun run(): Boolean = running.withLock {
        val config = settings.settings.first()
        val uri = config.autoBackupFolder ?: return@withLock false
        val ok = try {
            val password = config.autoBackupPassword?.let(seal::open) ?: throw IOException()
            val bytes = try {
                val snapshot = repository.snapshot()
                withContext(Dispatchers.Default) { BackupCodec.encode(snapshot, password) }
            } finally { password.fill('\u0000') }
            val target = folders.open(uri)
            target.write(autoBackupName(now()), bytes)
            bytes.fill(0)
            autoBackupsToDelete(target.names()).forEach { target.delete(it) }
            true
        } catch (_: Exception) { false }
        val date = now().toLocalDate()
        settings.update { it.copy(autoBackupLast = if (ok) date else it.autoBackupLast, autoBackupFailed = !ok) }
        ok
    }

    /** Stores the folder and the sealed password, then writes the first backup right away. */
    suspend fun enable(uri: String, password: CharArray, interval: AutoBackupInterval): Boolean {
        val previous = settings.settings.first().autoBackupFolder
        val name = try { folders.keep(uri) } catch (_: Exception) { return false }
        val sealed = seal.seal(password)
        if (previous != null && previous != uri) runCatching { folders.release(previous) }
        settings.update { it.copy(autoBackupFolder = uri, autoBackupFolderName = name, autoBackupInterval = interval,
            autoBackupPassword = sealed, autoBackupLast = null, autoBackupFailed = false) }
        return run()
    }

    suspend fun setInterval(interval: AutoBackupInterval) = settings.update { it.copy(autoBackupInterval = interval) }

    /** Forgets folder and password; backups already saved stay where they are. */
    suspend fun turnOff() {
        settings.settings.first().autoBackupFolder?.let { runCatching { folders.release(it) } }
        settings.update { it.copy(autoBackupFolder = null, autoBackupFolderName = null, autoBackupPassword = null,
            autoBackupLast = null, autoBackupFailed = false) }
    }
}

class TreeAccess(private val resolver: ContentResolver) : FolderAccess {
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    override fun keep(uri: String): String {
        val tree = Uri.parse(uri)
        resolver.takePersistableUriPermission(tree, flags)
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return resolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: tree.lastPathSegment.orEmpty()
    }

    override fun release(uri: String) = resolver.releasePersistableUriPermission(Uri.parse(uri), flags)
    override fun open(uri: String): BackupFolder = TreeFolder(resolver, Uri.parse(uri))
}

/** A document tree granted through the system folder picker. */
class TreeFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val parent get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun children(): Map<String, Uri> {
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = resolver.query(list, columns, null, null, null) ?: throw IOException()
        return cursor.use {
            buildMap { while (it.moveToNext()) put(it.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))) }
        }
    }

    override suspend fun names() = withContext(Dispatchers.IO) { children().keys.toList() }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: throw IOException()
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException()
        stream.use { it.write(bytes); it.flush() }
    }

    override suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        children()[name]?.let { DocumentsContract.deleteDocument(resolver, it) }
        Unit
    }
}
