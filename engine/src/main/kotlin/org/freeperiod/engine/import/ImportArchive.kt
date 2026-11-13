package org.freeperiod.engine.importing

/**
 * Another app's export often arrives as a ZIP: Clue's data download (password from the Clue app), Flo's export
 * (password from Flo's e-mail), or a backup someone zipped to send it. The files inside are read like a file chosen
 * on its own, so every app's reader works for both.
 */
object ImportArchive {
    private const val MAX_FILES = 8

    fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
        bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /**
     * The files to try, largest first; a file that is not a ZIP is returned as it is. Throws [ZipPasswordRequired]
     * or [ZipWrongPassword] for encrypted files; entries this reader can't unpack are left out.
     */
    fun files(bytes: ByteArray, password: CharArray? = null): List<ByteArray> {
        if (!isZip(bytes)) return listOf(bytes)
        val zip = ZipEntries(bytes)
        return zip.entries.filter { !it.name.endsWith("/") && !it.name.startsWith("__MACOSX/") && it.size > 0 }
            .sortedByDescending { it.size }.take(MAX_FILES)
            .mapNotNull { entry ->
                try { zip.read(entry, password) }
                catch (needed: ZipPasswordRequired) { throw needed }
                catch (wrong: ZipWrongPassword) { throw wrong }
                catch (_: Exception) { null }
            }
    }
}
