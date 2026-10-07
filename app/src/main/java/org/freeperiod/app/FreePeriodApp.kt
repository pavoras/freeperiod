package org.freeperiod.app

import android.app.Application
import androidx.work.Configuration
import kotlinx.coroutines.cancel

class FreePeriodApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.observeReminders()
    }

    override fun onTerminate() {
        container.applicationScope.cancel()
        super.onTerminate()
    }
}
