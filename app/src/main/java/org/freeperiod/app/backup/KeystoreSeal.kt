package org.freeperiod.app.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM with a non-exportable Android Keystore key; the sealed text is useless on any other device. */
class KeystoreSeal : PasswordSeal {
    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    override fun seal(password: CharArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        val plain = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val bytes = ByteArray(plain.remaining()).also { plain.get(it) }
        try {
            val sealed = cipher.iv + cipher.doFinal(bytes)
            return Base64.encodeToString(sealed, Base64.NO_WRAP)
        } finally { bytes.fill(0) }
    }

    override fun open(sealed: String): CharArray? = runCatching {
        val data = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(create = false) ?: return null, GCMParameterSpec(128, data, 0, IV_BYTES))
        }
        val plain = cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
        try {
            val chars = Charsets.UTF_8.decode(ByteBuffer.wrap(plain))
            CharArray(chars.remaining()).also { chars.get(it) }
        } finally { plain.fill(0) }
    }.getOrNull()

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "auto-backup-password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
