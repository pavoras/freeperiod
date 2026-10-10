package org.freeperiod.app.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.freeperiod.app.data.DatabaseTest
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.DayLog
import org.freeperiod.engine.backup.BackupCodec
import org.freeperiod.engine.backup.DecodeResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupTest : DatabaseTest() {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private val files = linkedMapOf<String, ByteArray>()
    private val kept = mutableSetOf<String>()
    private var keyLost = false
    private var unreachable = false
    private var now = LocalDateTime.of(2026, 4, 12, 9, 0)

    private val seal = object : PasswordSeal {
        override fun seal(password: CharArray) = "sealed:" + String(password).reversed()
        override fun open(sealed: String) = if (keyLost) null else sealed.removePrefix("sealed:").reversed().toCharArray()
    }
    private val folders = object : FolderAccess {
        override fun keep(uri: String): String { if (unreachable) throw SecurityException(); kept += uri; return "Backups" }
        override fun release(uri: String) { kept -= uri }
        override fun open(uri: String) = object : BackupFolder {
            override suspend fun names() = files.keys.toList()
            override suspend fun write(name: String, bytes: ByteArray) { if (unreachable) throw IOException(); files[name] = bytes.copyOf() }
            override suspend fun delete(name: String) { files.remove(name) }
        }
    }
    private lateinit var backup: AutoBackup

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temporary.root, "settings.preferences_pb") })
        backup = AutoBackup(repository, settings, seal, folders) { now }
    }

    @After fun tearDown() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    @Test fun keepsTheNewestFiveAndIgnoresOtherFiles() {
        val autos = (1..7).map { "freeperiod-auto-2026-04-0$it-0900.fpbackup" }
        val delete = autoBackupsToDelete(autos + "freeperiod-2026-04-01.fpbackup" + "notes.txt")
        assertEquals(autos.take(2).toSet(), delete.toSet())
    }

    @Test fun enableWritesARestorableBackupAndStoresOnlyTheSealedPassword() = runBlocking {
        repository.saveDayLog(DayLog(today, note = "Test"))
        assertTrue(backup.enable("content://tree/1", "secret-pass".toCharArray(), AutoBackupInterval.DAILY))
        val device = settings.settings.first()
        assertEquals("content://tree/1", device.autoBackupFolder)
        assertEquals("Backups", device.autoBackupFolderName)
        assertEquals(AutoBackupInterval.DAILY, device.autoBackupInterval)
        assertNotEquals("secret-pass", device.autoBackupPassword)
        assertEquals(now.toLocalDate(), device.autoBackupLast)
        assertFalse(device.autoBackupFailed)
        assertEquals(listOf("freeperiod-auto-2026-04-12-0900.fpbackup"), files.keys.toList())
        val decoded = BackupCodec.decode(files.values.single(), "secret-pass".toCharArray()) { today }
        assertEquals("Test", (decoded as DecodeResult.Ok).data.dayLogs.single().note)
    }

    @Test fun repeatedRunsKeepFiveFiles() = runBlocking {
        backup.enable("content://tree/1", "secret-pass".toCharArray(), AutoBackupInterval.WEEKLY)
        repeat(5) { now = now.plusDays(7); assertTrue(backup.run()) }
        assertEquals(5, files.size)
        assertFalse(files.containsKey("freeperiod-auto-2026-04-12-0900.fpbackup"))
    }

    @Test fun lostKeyOrFolderIsRecordedAsFailureAndKeepsTheLastDate() = runBlocking {
        backup.enable("content://tree/1", "secret-pass".toCharArray(), AutoBackupInterval.WEEKLY)
        keyLost = true
        now = now.plusDays(7)
        assertFalse(backup.run())
        assertTrue(settings.settings.first().autoBackupFailed)
        assertEquals(LocalDateTime.of(2026, 4, 12, 9, 0).toLocalDate(), settings.settings.first().autoBackupLast)
        keyLost = false
        unreachable = true
        assertFalse(backup.run())
        unreachable = false
        assertTrue(backup.run())
        assertFalse(settings.settings.first().autoBackupFailed)
    }

    @Test fun unreachableFolderLeavesAutomaticBackupOff() = runBlocking {
        unreachable = true
        assertFalse(backup.enable("content://tree/1", "secret-pass".toCharArray(), AutoBackupInterval.WEEKLY))
        assertNull(settings.settings.first().autoBackupFolder)
    }

    @Test fun turnOffForgetsFolderAndPasswordButKeepsFiles() = runBlocking {
        backup.enable("content://tree/1", "secret-pass".toCharArray(), AutoBackupInterval.WEEKLY)
        backup.turnOff()
        val device = settings.settings.first()
        assertNull(device.autoBackupFolder)
        assertNull(device.autoBackupPassword)
        assertTrue(kept.isEmpty())
        assertEquals(1, files.size)
        assertFalse(backup.run())
    }
}
