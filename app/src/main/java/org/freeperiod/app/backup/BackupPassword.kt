package org.freeperiod.app.backup

import org.freeperiod.app.R

/**
 * Backups are always encrypted, but nobody has to make up a password: an empty one
 * silently means [DEFAULT], and restoring always tries [DEFAULT] first. The app never shows it.
 */
object BackupPassword {
    const val DEFAULT = "freeperiod"

    /** The message for an unusable choice, or null. Both fields empty is fine: then [DEFAULT] is used. */
    fun problem(password: String, confirm: String): Int? = when {
        password.isEmpty() && confirm.isEmpty() -> null
        password.length < 8 -> R.string.backup_password_short
        password != confirm -> R.string.backup_password_mismatch
        else -> null
    }

    fun effective(password: String): CharArray = password.ifEmpty { DEFAULT }.toCharArray()

    /** Restore order: the default first, then what was typed (if anything else). */
    fun candidates(typed: String): List<String> = listOf(DEFAULT) + listOfNotNull(typed.takeIf { it.isNotEmpty() && it != DEFAULT })
}
