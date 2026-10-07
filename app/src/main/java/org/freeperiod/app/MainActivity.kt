package org.freeperiod.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.freeperiod.app.ui.theme.FreePeriodTheme
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.freeperiod.app.ui.nav.AppNav
import org.freeperiod.app.ui.today.TodayViewModel

class MainActivity : FragmentActivity() {
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
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            FreePeriodTheme(dynamicColor = settings?.dynamicColor ?: false) {
                settings?.let { AppNav(container, todayViewModel, it.onboardingDone) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        todayViewModel.onResume()
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
