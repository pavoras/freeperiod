package org.freeperiod.app.backup

import org.freeperiod.app.R
import org.junit.Assert.*
import org.junit.Test

class BackupPasswordTest {
    @Test fun emptyFieldsUseTheDefaultSilently() {
        assertNull(BackupPassword.problem("", ""))
        assertEquals("freeperiod", String(BackupPassword.effective("")))
        assertEquals("my own password", String(BackupPassword.effective("my own password")))
    }

    @Test fun ownPasswordsKeepTheirRules() {
        assertEquals(R.string.backup_password_short, BackupPassword.problem("short", "short"))
        assertEquals(R.string.backup_password_short, BackupPassword.problem("", "something"))
        assertEquals(R.string.backup_password_mismatch, BackupPassword.problem("long enough", "different"))
        assertNull(BackupPassword.problem("long enough", "long enough"))
    }

    @Test fun restoreTriesTheDefaultFirst() {
        assertEquals(listOf("freeperiod"), BackupPassword.candidates(""))
        assertEquals(listOf("freeperiod"), BackupPassword.candidates("freeperiod"))
        assertEquals(listOf("freeperiod", "typed one"), BackupPassword.candidates("typed one"))
    }
}
