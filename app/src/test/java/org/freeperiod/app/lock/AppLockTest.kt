package org.freeperiod.app.lock

import org.freeperiod.app.data.LockTimeout
import org.junit.Assert.*
import org.junit.Test

class AppLockTest {
    @Test fun coldStartLocks() { assertTrue(shouldLock(true, true, null, 0, LockTimeout.ONE_MINUTE)) }
    @Test fun withinTimeoutStaysOpen() { assertFalse(shouldLock(true, false, 1000, 60999, LockTimeout.ONE_MINUTE)) }
    @Test fun afterTimeoutLocks() { assertTrue(shouldLock(true, false, 1000, 61000, LockTimeout.ONE_MINUTE)) }
    @Test fun immediatelyLocksOnAnyBackground() { assertTrue(shouldLock(true, false, 1000, 1000, LockTimeout.IMMEDIATELY)) }
    @Test fun disabledNeverLocks() { assertFalse(shouldLock(false, true, 0, 999999, LockTimeout.IMMEDIATELY)) }
    @Test fun fiveMinutesUsesElapsedTime() {
        assertFalse(shouldLock(true, false, 1000, 300999, LockTimeout.FIVE_MINUTES))
        assertTrue(shouldLock(true, false, 1000, 301000, LockTimeout.FIVE_MINUTES))
    }
    @Test fun lockRequiresSecureDevice() {
        assertFalse(canEnableLock(deviceSecure = false, authenticationAvailable = true))
        assertFalse(canEnableLock(deviceSecure = true, authenticationAvailable = false))
        assertTrue(canEnableLock(deviceSecure = true, authenticationAvailable = true))
    }
    @Test fun unlockSurvivesConfigurationButNotColdStart() {
        val lock = AppLock()
        lock.resume(true, LockTimeout.ONE_MINUTE, 0)
        assertTrue(lock.locked.value)
        lock.unlock()
        lock.resume(true, LockTimeout.ONE_MINUTE, 1)
        assertFalse(lock.locked.value)
        val recreatedProcess = AppLock()
        recreatedProcess.resume(true, LockTimeout.ONE_MINUTE, 2)
        assertTrue(recreatedProcess.locked.value)
    }
    @Test fun backgroundTimeoutHidesPreviouslyUnlockedContent() {
        val lock = AppLock()
        lock.resume(true, LockTimeout.ONE_MINUTE, 0)
        lock.unlock()
        lock.background(1000)
        lock.resume(true, LockTimeout.ONE_MINUTE, 61000)
        assertTrue(lock.locked.value)
    }
}
