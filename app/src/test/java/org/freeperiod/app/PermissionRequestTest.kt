package org.freeperiod.app

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: an old transitive androidx.fragment (1.2.5 via biometric) rejected the large request
 * codes that activity-result launchers generate, crashing the notification permission request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionRequestTest {
    @Test
    fun permissionRequestFromFragmentActivityDoesNotCrash() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).create()
        val launcher = controller.get().registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        controller.start().resume()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
