package org.freeperiod.app

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.freeperiod.app.ui.theme.Accent
import org.freeperiod.app.ui.theme.FreePeriodTheme
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.freeperiod.app.data.AppSettings
import org.freeperiod.app.lock.*
import org.freeperiod.app.ui.nav.AppNav
import org.freeperiod.app.ui.today.TodayViewModel

class MainActivity : FragmentActivity() {
    private val appLock by lazy { ViewModelProvider(this)[AppLock::class.java] }
    private var deviceSettings by mutableStateOf<AppSettings?>(null)
    private var unlockMessage by mutableStateOf<Int?>(null)
    private lateinit var biometric: BiometricPrompt
    private val todayViewModel by lazy {
        val container = (application as FreePeriodApp).container
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TodayViewModel(container.repository, container.settings, container.clock) as T
        })[TodayViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FreePeriodApp).container
        biometric = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlockMessage = null
                appLock.unlock()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { unlockMessage = R.string.lock_auth_error }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settings.settings.collect { value ->
                    if (value.lockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        appLock.resume(value.lockEnabled, value.lockTimeout, SystemClock.elapsedRealtime())
                    }
                    deviceSettings = value
                }
            }
        }
        setContent {
            val settings = deviceSettings
            val locked by appLock.locked.collectAsStateWithLifecycle()
            val navigationState = rememberSaveableStateHolder()
            FreePeriodTheme(accent = settings?.accent ?: Accent.CORAL) {
                settings?.let {
                    if (it.lockEnabled && locked) LockedScreen(::authenticate, unlockMessage)
                    else navigationState.SaveableStateProvider("app-navigation") {
                        AppNav(container, todayViewModel, it.onboardingDone)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        todayViewModel.onResume()
        deviceSettings?.let { appLock.resume(it.lockEnabled, it.lockTimeout, SystemClock.elapsedRealtime()) }
    }

    override fun onStop() {
        if (!isChangingConfigurations) appLock.background(SystemClock.elapsedRealtime())
        super.onStop()
    }

    private fun authenticate() {
        if (!lockAvailable(this)) { unlockMessage = R.string.lock_unavailable; return }
        unlockMessage = null
        biometric.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock))
            .setSubtitle(getString(R.string.locked_message)).setAllowedAuthenticators(AUTHENTICATORS).build())
    }
}

@Composable
fun Placeholder() {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        }
    }
}
