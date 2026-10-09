package org.freeperiod.app.lock

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freeperiod.app.data.LockTimeout

fun shouldLock(enabled: Boolean, coldStart: Boolean, backgroundedAtMs: Long?, nowMs: Long, timeout: LockTimeout): Boolean {
    if (!enabled) return false
    if (coldStart) return true
    if (backgroundedAtMs == null) return false
    val duration = when (timeout) {
        LockTimeout.IMMEDIATELY -> 0L
        LockTimeout.ONE_MINUTE -> 60_000L
        LockTimeout.FIVE_MINUTES -> 300_000L
        LockTimeout.TEN_MINUTES -> 600_000L
        LockTimeout.FIFTEEN_MINUTES -> 900_000L
    }
    return nowMs - backgroundedAtMs >= duration
}

internal fun canEnableLock(deviceSecure: Boolean, authenticationAvailable: Boolean): Boolean = deviceSecure && authenticationAvailable

fun lockAvailable(context: Context): Boolean = canEnableLock(
    context.getSystemService(KeyguardManager::class.java).isDeviceSecure,
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS)

const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

class AppLock : ViewModel() {
    private val mutableLocked = MutableStateFlow(true)
    val locked = mutableLocked.asStateFlow()
    private var coldStart = true
    private var backgroundedAt: Long? = null
    fun resume(enabled: Boolean, timeout: LockTimeout, nowMs: Long) {
        if (!enabled) mutableLocked.value = false
        else if (shouldLock(true, coldStart, backgroundedAt, nowMs, timeout)) mutableLocked.value = true
        coldStart = false
        backgroundedAt = null
    }
    fun background(nowMs: Long) { backgroundedAt = nowMs }
    fun unlock() { mutableLocked.value = false }
}
