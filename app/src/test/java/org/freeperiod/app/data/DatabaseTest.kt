package org.freeperiod.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.freeperiod.app.data.db.FreePeriodDatabase
import org.junit.After
import org.junit.Before

abstract class DatabaseTest {
    protected lateinit var db: FreePeriodDatabase
    protected lateinit var repository: Repository
    protected val today: LocalDate = LocalDate.of(2026, 4, 12)

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            FreePeriodDatabase::class.java).allowMainThreadQueries().build()
        repository = Repository(db) { today }
    }

    @After fun closeDatabase() { db.close() }
}
