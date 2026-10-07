package org.freeperiod.app

import android.content.Context
import androidx.room.Room
import java.time.LocalDate
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.app.data.db.FreePeriodDatabase

class AppContainer(context: Context) {
    val clock: () -> LocalDate = { LocalDate.now() }
    private val database = Room.databaseBuilder(context.applicationContext,
        FreePeriodDatabase::class.java, "freeperiod.db").build()
    val repository = Repository(database, clock)
    val settings = SettingsStore(context)
}
